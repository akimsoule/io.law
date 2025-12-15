package bj.gouv.sgg.batch.processor;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.exception.FetchException;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.util.RateLimitHandler;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.batch.item.ItemProcessor;

import java.io.IOException;

@Slf4j
@AllArgsConstructor
public abstract class AbstractFetchProcessor implements ItemProcessor<LawDocument, LawDocument> {

    private final LawProperties properties;
    private final RateLimitHandler rateLimitHandler;

    /**
     * Vérifie l'existence d'un document via requête HEAD HTTP.
     * 
     * <p><b>⚠️ RÉSILIENCE</b> : Ne doit JAMAIS throw d'exception. En cas d'erreur,
     * marque le document FAILED et retourne pour continuer le job.
     */
    @Override
    public LawDocument process(LawDocument document) {
        String url = document.getUrl();
        log.info("🔍 Fetching: {}", url);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            // Tester l'URL principale avec gestion rate limiting
            int statusCode = rateLimitHandler.executeWithRetry(url,
                    urlToCheck -> checkUrlInternal(client, urlToCheck));

            if (statusCode == 200) {
                document.setExists(true);
                document.setStatus(LawDocument.ProcessingStatus.FETCHED);
                log.info("✅ Found (200): {} → {}", document.getDocumentId(), url);
                return document;
            }

            // Si 404 et number < 10, tester aussi avec padding (01, 02, etc.)
            if (statusCode == 404 && document.getNumber() < 10) {
                String urlWithPadding = buildUrlWithPadding(document);
                log.info("🔍 Trying padded format: {}", urlWithPadding);

                int paddedStatusCode = rateLimitHandler.executeWithRetry(urlWithPadding,
                        urlToCheck -> checkUrlInternal(client, urlToCheck));

                if (paddedStatusCode == 200) {
                    document.setUrl(urlWithPadding); // Update URL to the working one
                    document.setExists(true);
                    document.setStatus(LawDocument.ProcessingStatus.FETCHED);
                    log.info("✅ Found (200) with padding: {} → {}", document.getDocumentId(), urlWithPadding);
                    return document;
                }
            }

            // Si 429 après tous les retries → RATE_LIMITED (sera repris)
            if (statusCode == 429) {
                log.warn("⚠️ Rate limited (429) after retries: {} → {} [RATE_LIMITED - will be retried]", 
                         document.getDocumentId(), url);
                document.setExists(false);
                document.setStatus(LawDocument.ProcessingStatus.RATE_LIMITED);
                return document; // Continue le job, sera repris plus tard
            }

            // Aucune URL ne fonctionne (404) → FAILED définitif
            document.setExists(false);
            document.setStatus(LawDocument.ProcessingStatus.FAILED);
            log.debug("❌ Not found (404): {} → {} [FAILED - permanent]", document.getDocumentId(), url);
            return document;

        } catch (IOException e) {
            // ❌ NE PAS throw : marquer FAILED et continuer le job
            log.error("💥 Error fetching {} ({}): {}", document.getDocumentId(), url, e.getMessage());
            document.setExists(false);
            document.setStatus(LawDocument.ProcessingStatus.FAILED);
            return document; // Continue le job même en cas d'erreur
        }
    }

    private int checkUrlInternal(CloseableHttpClient client, String url) {
        try {
            HttpHead request = new HttpHead(url);
            request.setHeader("User-Agent", properties.getUserAgent());

            return client.execute(request, response -> {
                if (response.getEntity() != null) {
                    EntityUtils.consume(response.getEntity());
                }
                return response.getCode();
            });
        } catch (IOException e) {
            log.error("Error checking URL {}: {}", url, e.getMessage());
            return 500; // Erreur serveur
        }
    }

    private String buildUrlWithPadding(LawDocument document) {
        // Format: https://sgg.gouv.bj/doc/loi-2024-01
        return String.format("%s/%s-%d-%02d",
                properties.getBaseUrl(),
                document.getType(),
                document.getYear(),
                document.getNumber()
        );
    }
}
