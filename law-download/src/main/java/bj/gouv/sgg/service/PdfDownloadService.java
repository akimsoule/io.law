package bj.gouv.sgg.service;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.exception.DownloadException;
import bj.gouv.sgg.exception.DownloadHttpException;
import bj.gouv.sgg.exception.DownloadEmptyPdfException;
import bj.gouv.sgg.util.RateLimitHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Service de téléchargement de PDFs avec validation.
 * Service stateless : pas de persistance, juste téléchargement et retour du hash.
 */
@Slf4j
public class PdfDownloadService {
    
    private static final int TIMEOUT_MS = 30000;  // 30 secondes
    private static final int MAX_RETRIES = 3;     // 3 tentatives
    private static final long RETRY_DELAY_MS = 2000L;  // 2 secondes
    
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String userAgent;
    private final RateLimitHandler rateLimitHandler;
    
    public PdfDownloadService() {
        AppConfig config = AppConfig.get();
        this.baseUrl = config.getBaseUrl();
        this.userAgent = config.getUserAgent();
        this.rateLimitHandler = new RateLimitHandler();
        
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofMillis(TIMEOUT_MS))
                .build();
    }
    
    /**
     * Télécharge un PDF et calcule son SHA-256.
     * Service stateless : ne sauvegarde rien en base.
     * 
     * @param type Type du document
     * @param year Année
     * @param number Numéro (peut être String avec padding)
     * @param destinationPath Chemin de destination
     * @return Le hash SHA-256 du fichier téléchargé
     * @throws DownloadHttpException si erreur HTTP
     * @throws DownloadEmptyPdfException si fichier trop petit
     */
    public String downloadPdf(String type, int year, String number, Path destinationPath) {
        String documentId = String.format("%s-%d-%s", type, year, number);
        String url = buildUrl(type, year, number);
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return attemptDownload(destinationPath, documentId, url, attempt);
                
            } catch (DownloadException e) {
                throw e;  // Remonter directement les erreurs métier
                
            } catch (IOException | InterruptedException e) {
                if (attempt == MAX_RETRIES) {
                    log.error("❌ Failed to download {} after {} attempts", url, MAX_RETRIES);
                    throw new DownloadHttpException(url, e);
                }
                handleRetryDelay(url, attempt, e);
            }
        }
        
        // Ne devrait jamais arriver ici
        throw new DownloadHttpException(url, new IOException("Failed after " + MAX_RETRIES + " attempts"));
    }
    
    /**
     * Tente un téléchargement unique.
     */
    private String attemptDownload(Path destinationPath, 
                                   String documentId, String url, int attempt) 
            throws IOException, InterruptedException {
        // Appliquer rate limiting avant chaque requête
        rateLimitHandler.beforeRequest();
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("User-Agent", userAgent)
                .timeout(Duration.ofMillis(TIMEOUT_MS))
                .build();
        
        HttpResponse<InputStream> response = httpClient.send(
                request, 
                HttpResponse.BodyHandlers.ofInputStream()
        );
        
        // Gérer erreur 429 (Too Many Requests)
        if (response.statusCode() == 429) {
            handleRateLimitError(url, documentId, attempt);
        }
        
        if (response.statusCode() != 200) {
            throw new DownloadHttpException(url, response.statusCode());
        }
        
        return processSuccessfulResponse(response, destinationPath, documentId);
    }
    
    /**
     * Gère une erreur 429 (rate limit).
     */
    private void handleRateLimitError(String url, String documentId, int attempt) throws IOException, InterruptedException {
        rateLimitHandler.on429(url);
        if (attempt < MAX_RETRIES) {
            log.warn("⚠️ Rate limit hit, retrying {} (attempt {}/{})", documentId, attempt, MAX_RETRIES);
            Thread.sleep(rateLimitHandler.getStats().currentDelayMs);
            throw new IOException("Rate limit, will retry");
        }
        throw new DownloadHttpException(url, 429);
    }
    
    /**
     * Traite une réponse HTTP réussie.
     */
    private String processSuccessfulResponse(HttpResponse<InputStream> response, 
                                             Path destinationPath, String documentId) throws IOException {
        ensureDirectoryExists(destinationPath, documentId);
        
        String hash;
        try (InputStream in = response.body()) {
            hash = downloadAndHash(in, destinationPath, documentId);
        }
        
        long fileSize = validateFileSize(destinationPath, documentId);
        
        log.debug("✅ Downloaded: {} ({} bytes, sha256: {})", 
                 destinationPath.getFileName(), fileSize, hash.substring(0, 8));
        
        return hash;
    }
    
    /**
     * Gère le délai entre les retries.
     */
    private void handleRetryDelay(String url, int attempt, Exception e) {
        log.debug("Retry {}/{} for {}: {}", attempt, MAX_RETRIES, url, e.getMessage());
        try {
            Thread.sleep(RETRY_DELAY_MS * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new DownloadHttpException(url, ie);
        }
    }
    
    /**
     * Crée le répertoire parent si nécessaire.
     */
    private void ensureDirectoryExists(Path destinationPath, String documentId) {
        try {
            Files.createDirectories(destinationPath.getParent());
        } catch (IOException e) {
            throw new DownloadException("Failed to create directory for " + documentId, e);
        }
    }
    
    /**
     * Valide la taille du fichier téléchargé.
     */
    private long validateFileSize(Path destinationPath, String documentId) {
        try {
            long fileSize = Files.size(destinationPath);
            if (fileSize < 1024) { // Moins de 1 KB = suspect
                deleteQuietly(destinationPath);
                throw new DownloadEmptyPdfException(documentId, String.format("File size: %d bytes", fileSize));
            }
            return fileSize;
        } catch (IOException e) {
            throw new DownloadException("Failed to check file size for " + documentId, e);
        }
    }
    
    /**
     * Supprime un fichier sans lever d'exception.
     */
    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // Suppression best-effort, l'erreur n'est pas critique
            log.debug("Failed to delete file {}: {}", path, e.getMessage());
        }
    }
    
    /**
     * Télécharge le fichier et calcule son SHA-256 en une seule passe.
     */
    private String downloadAndHash(InputStream inputStream, Path destination, String documentId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Lire et hasher en même temps
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            try (var out = Files.newOutputStream(destination)) {
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                    out.write(buffer, 0, bytesRead);
                }
            }
            
            byte[] hashBytes = digest.digest();
            return HexFormat.of().formatHex(hashBytes);
            
        } catch (java.security.NoSuchAlgorithmException e) {
            // Nettoyer fichier partiel en cas d'erreur
            deleteQuietly(destination);
            throw new DownloadException("SHA-256 algorithm not available for " + documentId, e);
            
        } catch (IOException e) {
            // Nettoyer fichier partiel en cas d'erreur
            deleteQuietly(destination);
            throw new DownloadException("Failed to download and hash " + documentId, e);
        }
    }
    
    /**
     * Valide qu'un fichier est bien un PDF (magic bytes).
     * 
     * @param path Chemin du fichier
     * @return true si le fichier commence par %PDF
     */
    public boolean validatePdfFormat(Path path) {
        try {
            byte[] header = new byte[4];
            try (InputStream in = Files.newInputStream(path)) {
                int read = in.read(header);
                if (read < 4) {
                    return false;
                }
            }
            
            // Magic bytes PDF : %PDF (0x25504446)
            return header[0] == 0x25 && 
                   header[1] == 0x50 && 
                   header[2] == 0x44 && 
                   header[3] == 0x46;
                   
        } catch (IOException e) {
            log.error("Error validating PDF format: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Construit l'URL de téléchargement d'un document.
     */
    private String buildUrl(String type, int year, String number) {
        return String.format("%s/%s/%s-%d-%s.pdf", baseUrl, type, type, year, number);
    }
}
