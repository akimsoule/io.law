package bj.gouv.sgg.service;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.exception.FetchHttpException;
import bj.gouv.sgg.exception.FetchTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Service HTTP pour vérifier l'existence des documents via HEAD requests.
 * Service stateless qui ne gère pas la persistance.
 */
@Slf4j
@Service
public class HttpCheckService {
    
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String userAgent;
    private final int timeout;
    private final int maxRetries;
    private final long retryDelay;
    
    public HttpCheckService(AppConfig config) {
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
     * Service stateless : ne gère pas la persistance, juste la vérification HTTP.
     * 
     * @param type Type du document ("loi" ou "decret")
     * @param year Année du document
     * @param number Numéro du document
     * @return true si le document existe (HTTP 200), false si 404
     * @throws FetchHttpException si erreur HTTP autre que 404
     * @throws FetchTimeoutException si timeout après toutes les tentatives
     */
    public boolean checkDocumentExists(String type, int year, String number) {
        String documentId = String.format("%s-%d-%s", type, year, number);
        String url = buildUrl(type, year, number);
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .header("User-Agent", userAgent)
                        .timeout(Duration.ofMillis(timeout))
                        .build();

                log.info("fetching (HEAD) {} (attempt {}/{})", url, attempt, maxRetries);
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                
                int statusCode = response.statusCode();
                
                if (statusCode == 200) {
                    log.info("✅ Found: {}", documentId);
                    return true;
                } else if (statusCode == 404) {
                    log.debug("❌ Not found: {}", documentId);
                    return false;
                } else {
                    log.warn("⚠️ Unexpected status {} for {}", statusCode, url);
                    throw new FetchHttpException(url, statusCode);
                }
                
            } catch (IOException e) {
                if (attempt == maxRetries) {
                    log.error("❌ IO error checking {} after {} retries", url, maxRetries);
                    throw new FetchHttpException(url, e);
                }
                
                log.debug("Retry {}/{} for {}: {}", attempt, maxRetries, url, e.getMessage());
                
                try {
                    Thread.sleep(retryDelay * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new FetchTimeoutException(String.format("%s-%d-%s", type, year, number));
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FetchTimeoutException(String.format("%s-%d-%s", type, year, number));
            }
        }
        
        throw new FetchTimeoutException(String.format("%s-%d-%s", type, year, number));
    }
    
    /**
     * Construit l'URL d'un document.
     */
    public String buildUrl(String type, int year, String number) {
        return String.format("%s/%s-%d-%s/", baseUrl, type, year, number);
    }
}
