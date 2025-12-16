package bj.gouv.sgg.service;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.exception.DownloadException;
import bj.gouv.sgg.exception.DownloadHttpException;
import bj.gouv.sgg.exception.DownloadEmptyPdfException;
import bj.gouv.sgg.exception.DownloadHashException;
import bj.gouv.sgg.entity.DownloadResultEntity;
import bj.gouv.sgg.repository.impl.JpaDownloadResultRepository;
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
import java.util.Optional;

/**
 * Service de téléchargement de PDFs avec validation.
 */
@Slf4j
public class PdfDownloadService {
    
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String userAgent;
    private final int timeout;
    private final int maxRetries;
    private final long retryDelay;
    private final JpaDownloadResultRepository repository;
    
    public PdfDownloadService() {
        AppConfig config = AppConfig.get();
        this.baseUrl = config.getBaseUrl();
        this.userAgent = config.getUserAgent();
        this.timeout = config.getHttpTimeout();
        this.maxRetries = config.getMaxRetries();
        this.retryDelay = config.getRetryDelay();
        this.repository = new JpaDownloadResultRepository(
            bj.gouv.sgg.config.DatabaseConfig.getInstance().createEntityManager()
        );
        
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofMillis(timeout))
                .build();
    }
    
    /**
     * Télécharge un PDF et calcule son SHA-256.
     * Sauvegarde le résultat dans le repository pour idempotence.
     * 
     * @param type Type du document
     * @param year Année
     * @param number Numéro
     * @param destinationPath Chemin de destination
     * @return Le hash SHA-256 du fichier téléchargé
     * @throws DownloadHttpException si erreur HTTP
     * @throws DownloadEmptyPdfException si fichier trop petit
     * @throws DownloadHashException si erreur calcul hash
     */
    public String downloadPdf(String type, int year, int number, Path destinationPath) {
        String documentId = String.format("%s-%d-%d", type, year, number);
        
        // Vérifier si déjà téléchargé (idempotence)
        Optional<DownloadResultEntity> existingResult = repository.findByDocumentId(documentId);
        if (existingResult.isPresent() && existingResult.get().isSuccess()) {
            if (Files.exists(destinationPath)) {
                log.debug("⏭️ Déjà téléchargé: {}", documentId);
                return existingResult.get().getSha256Hash();
            }
        }
        
        String url = buildUrl(type, year, number);
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .header("User-Agent", userAgent)
                        .timeout(Duration.ofMillis(timeout))
                        .build();
                
                HttpResponse<InputStream> response = httpClient.send(
                        request, 
                        HttpResponse.BodyHandlers.ofInputStream()
                );
                
                if (response.statusCode() != 200) {
                    throw new DownloadHttpException(url, response.statusCode());
                }
                
                // Créer répertoire parent si nécessaire
                try {
                    Files.createDirectories(destinationPath.getParent());
                } catch (IOException e) {
                    throw new DownloadException("Failed to create directory for " + documentId, e);
                }
                
                // Télécharger et calculer hash en une passe
                String hash;
                try (InputStream in = response.body()) {
                    hash = downloadAndHash(in, destinationPath, documentId);
                }
                
                // Valider taille minimale (éviter PDFs vides ou corrompus)
                long fileSize;
                try {
                    fileSize = Files.size(destinationPath);
                } catch (IOException e) {
                    throw new DownloadException("Failed to check file size for " + documentId, e);
                }
                
                if (fileSize < 1024) { // Moins de 1 KB = suspect
                    try {
                        Files.deleteIfExists(destinationPath);
                    } catch (IOException ignored) {}
                    throw new DownloadEmptyPdfException(documentId, String.format("File size: %d bytes", fileSize));
                }
                
                log.debug("✅ Downloaded: {} ({} bytes, sha256: {})", 
                         destinationPath.getFileName(), fileSize, hash.substring(0, 8));
                
                // Sauvegarder succès
                DownloadResultEntity result = DownloadResultEntity.builder()
                    .documentId(documentId)
                    .type(type)
                    .year(year)
                    .number(number)
                    .success(true)
                    .fileSize(fileSize)
                    .sha256Hash(hash)
                    .build();
                repository.save(result);
                
                return hash;
                
            } catch (DownloadException e) {
                // Ne pas retry les erreurs métier
                DownloadResultEntity result = DownloadResultEntity.builder()
                    .documentId(documentId)
                    .type(type)
                    .year(year)
                    .number(number)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
                repository.save(result);
                throw e;
                
            } catch (IOException | InterruptedException e) {
                if (attempt == maxRetries) {
                    log.error("❌ Failed to download {} after {} attempts", url, maxRetries);
                    DownloadResultEntity result = DownloadResultEntity.builder()
                        .documentId(documentId)
                        .type(type)
                        .year(year)
                        .number(number)
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build();
                    repository.save(result);
                    throw new DownloadHttpException(url, e);
                }
                
                log.debug("Retry {}/{} for {}: {}", attempt, maxRetries, url, e.getMessage());
                
                try {
                    Thread.sleep(retryDelay * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new DownloadHttpException(url, ie);
                }
            }
        }
        
        DownloadResultEntity result = DownloadResultEntity.builder()
            .documentId(documentId)
            .type(type)
            .year(year)
            .number(number)
            .success(false)
            .errorMessage("Failed after " + maxRetries + " attempts")
            .build();
        repository.save(result);
        throw new DownloadHttpException(url, new IOException("Failed after " + maxRetries + " attempts"));
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
            try {
                Files.deleteIfExists(destination);
            } catch (IOException ignored) {}
            throw new DownloadException("SHA-256 algorithm not available for " + documentId, e);
            
        } catch (IOException e) {
            // Nettoyer fichier partiel en cas d'erreur
            try {
                Files.deleteIfExists(destination);
            } catch (IOException ignored) {}
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
    private String buildUrl(String type, int year, int number) {
        return String.format("%s/%s/%s-%d-%d.pdf", baseUrl, type, type, year, number);
    }
}
