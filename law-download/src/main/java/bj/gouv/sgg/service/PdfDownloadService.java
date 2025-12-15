package bj.gouv.sgg.service;

import bj.gouv.sgg.config.AppConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

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
    
    public PdfDownloadService() {
        AppConfig config = AppConfig.get();
        this.baseUrl = config.getBaseUrl();
        this.userAgent = config.getUserAgent();
        this.timeout = config.getHttpTimeout();
        this.maxRetries = config.getMaxRetries();
        this.retryDelay = config.getRetryDelay();
        
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofMillis(timeout))
                .build();
    }
    
    /**
     * Télécharge un PDF et calcule son SHA-256.
     * 
     * @param type Type du document
     * @param year Année
     * @param number Numéro
     * @param destinationPath Chemin de destination
     * @return Le hash SHA-256 du fichier téléchargé
     * @throws IOException En cas d'erreur de téléchargement
     */
    public String downloadPdf(String type, int year, int number, Path destinationPath) throws IOException {
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
                    throw new IOException("HTTP " + response.statusCode() + " for " + url);
                }
                
                // Créer répertoire parent si nécessaire
                Files.createDirectories(destinationPath.getParent());
                
                // Télécharger et calculer hash en une passe
                String hash;
                try (InputStream in = response.body()) {
                    hash = downloadAndHash(in, destinationPath);
                }
                
                // Valider taille minimale (éviter PDFs vides ou corrompus)
                long fileSize = Files.size(destinationPath);
                if (fileSize < 1024) { // Moins de 1 KB = suspect
                    Files.deleteIfExists(destinationPath);
                    throw new IOException("Downloaded file too small: " + fileSize + " bytes");
                }
                
                log.debug("✅ Downloaded: {} ({} bytes, sha256: {})", 
                         destinationPath.getFileName(), fileSize, hash.substring(0, 8));
                
                return hash;
                
            } catch (IOException e) {
                if (attempt == maxRetries) {
                    log.error("❌ Failed to download {} after {} attempts: {}", 
                             url, maxRetries, e.getMessage());
                    throw e;
                }
                
                log.warn("⚠️ Download attempt {}/{} failed for {}: {}", 
                        attempt, maxRetries, url, e.getMessage());
                
                try {
                    Thread.sleep(retryDelay * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Download interrupted", ie);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Download interrupted", e);
            }
        }
        
        throw new IOException("Failed to download after " + maxRetries + " attempts");
    }
    
    /**
     * Télécharge le fichier et calcule son SHA-256 en une seule passe.
     */
    private String downloadAndHash(InputStream inputStream, Path destination) throws IOException {
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
            
        } catch (Exception e) {
            // Nettoyer fichier partiel en cas d'erreur
            Files.deleteIfExists(destination);
            throw new IOException("Failed to download and hash", e);
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
