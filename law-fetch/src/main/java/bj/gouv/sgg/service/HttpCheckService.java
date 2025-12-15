package bj.gouv.sgg.service;

import bj.gouv.sgg.config.AppConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Service HTTP pour vérifier l'existence des documents via HEAD requests.
 */
@Slf4j
public class HttpCheckService {
    
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String userAgent;
    private final int timeout;
    private final int maxRetries;
    private final long retryDelay;
    
    public HttpCheckService() {
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
     * Vérifie si un document existe via HEAD request.
     * 
     * @param type Type du document ("loi" ou "decret")
     * @param year Année du document
     * @param number Numéro du document
     * @return true si le document existe (HTTP 200), false sinon
     */
    public boolean checkDocumentExists(String type, int year, int number) {
        String url = buildUrl(type, year, number);
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .header("User-Agent", userAgent)
                        .timeout(Duration.ofMillis(timeout))
                        .build();
                
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                
                int statusCode = response.statusCode();
                
                if (statusCode == 200) {
                    return true;
                } else if (statusCode == 404) {
                    return false;
                } else {
                    log.warn("⚠️ Unexpected status {} for {}", statusCode, url);
                    return false;
                }
                
            } catch (IOException | InterruptedException e) {
                if (attempt == maxRetries) {
                    log.warn("⚠️ Error checking {} after {} retries: {}", url, maxRetries, e.getMessage());
                    return false;
                }
                
                try {
                    Thread.sleep(retryDelay * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Construit l'URL d'un document.
     */
    private String buildUrl(String type, int year, int number) {
        return String.format("%s/%s/%s-%d-%d.pdf", baseUrl, type, type, year, number);
    }
}
