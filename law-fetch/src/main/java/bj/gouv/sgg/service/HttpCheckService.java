package bj.gouv.sgg.service;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.exception.FetchHttpException;
import bj.gouv.sgg.exception.FetchTimeoutException;
import bj.gouv.sgg.entity.FetchResultEntity;
import bj.gouv.sgg.repository.impl.JpaFetchResultRepository;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

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
    private final JpaFetchResultRepository repository;
    
    public HttpCheckService() {
        AppConfig config = AppConfig.get();
        this.baseUrl = config.getBaseUrl();
        this.userAgent = config.getUserAgent();
        this.timeout = config.getHttpTimeout();
        this.maxRetries = config.getMaxRetries();
        this.retryDelay = config.getRetryDelay();
        this.repository = new JpaFetchResultRepository(
            bj.gouv.sgg.config.DatabaseConfig.getInstance().createEntityManager()
        );
        
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofMillis(timeout))
                .build();
    }
    
    /**
     * Vérifie si un document existe via HEAD request.
     * Sauvegarde le résultat dans le repository pour idempotence.
     * 
     * @param type Type du document ("loi" ou "decret")
     * @param year Année du document
     * @param number Numéro du document
     * @return true si le document existe (HTTP 200), false si 404
     * @throws FetchHttpException si erreur HTTP autre que 404
     * @throws FetchTimeoutException si timeout après toutes les tentatives
     */
    public boolean checkDocumentExists(String type, int year, int number) {
        String documentId = String.format("%s-%d-%d", type, year, number);
        
        // Vérifier si déjà vérifié (idempotence)
        Optional<FetchResultEntity> existingResult = repository.findByDocumentId(documentId);
        if (existingResult.isPresent()) {
            log.debug("⏭️ Déjà vérifié: {} → {}", documentId, existingResult.get().isFound());
            return existingResult.get().isFound();
        }
        
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
                    // Sauvegarder succès
                    FetchResultEntity result = FetchResultEntity.builder()
                        .documentId(documentId)
                        .type(type)
                        .year(year)
                        .number(number)
                        .found(true)
                        .httpStatus(statusCode)
                        .build();
                    repository.save(result);
                    return true;
                } else if (statusCode == 404) {
                    // Sauvegarder échec
                    FetchResultEntity result = FetchResultEntity.builder()
                        .documentId(documentId)
                        .type(type)
                        .year(year)
                        .number(number)
                        .found(false)
                        .httpStatus(statusCode)
                        .errorMessage("Not found")
                        .build();
                    repository.save(result);
                    return false;
                } else {
                    log.warn("⚠️ Unexpected status {} for {}", statusCode, url);
                    FetchResultEntity result = FetchResultEntity.builder()
                        .documentId(documentId)
                        .type(type)
                        .year(year)
                        .number(number)
                        .found(false)
                        .httpStatus(statusCode)
                        .errorMessage("Unexpected status")
                        .build();
                    repository.save(result);
                    throw new FetchHttpException(url, statusCode);
                }
                
            } catch (IOException e) {
                if (attempt == maxRetries) {
                    log.error("❌ IO error checking {} after {} retries", url, maxRetries);
                    FetchResultEntity result = FetchResultEntity.builder()
                        .documentId(documentId)
                        .type(type)
                        .year(year)
                        .number(number)
                        .found(false)
                        .httpStatus(0)
                        .errorMessage(e.getMessage())
                        .build();
                    repository.save(result);
                    throw new FetchHttpException(url, e);
                }
                
                log.debug("Retry {}/{} for {}: {}", attempt, maxRetries, url, e.getMessage());
                
                try {
                    Thread.sleep(retryDelay * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new FetchTimeoutException(String.format("%s-%d-%d", type, year, number));
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FetchTimeoutException(String.format("%s-%d-%d", type, year, number));
            }
        }
        
        throw new FetchTimeoutException(String.format("%s-%d-%d", type, year, number));
    }
    
    /**
     * Construit l'URL d'un document.
     */
    public String buildUrl(String type, int year, int number) {
        return String.format("%s/%s/%s-%d-%d.pdf", baseUrl, type, type, year, number);
    }
}
