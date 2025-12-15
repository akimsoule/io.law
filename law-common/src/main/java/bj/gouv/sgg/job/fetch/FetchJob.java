package bj.gouv.sgg.job.fetch;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.model.DocumentRecord;
import bj.gouv.sgg.model.ProcessingStatus;
import bj.gouv.sgg.service.DocumentService;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Job de fetch sans Spring Batch.
 * Récupère les métadonnées des documents depuis le site SGG.
 */
@Slf4j
public class FetchJob {
    
    private final AppConfig config;
    private final DocumentService documentService;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    
    public FetchJob() {
        this.config = AppConfig.get();
        this.documentService = new DocumentService();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getHttpTimeout()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.executor = Executors.newFixedThreadPool(config.getMaxThreads());
    }
    
    /**
     * Exécute le fetch pour l'année courante (mode current).
     */
    public void runCurrent(String type) {
        int currentYear = LocalDate.now().getYear();
        log.info("🔍 fetchCurrent: type={}, year={}", type, currentYear);
        
        List<DocumentRecord> documentsToCheck = new ArrayList<>();
        for (int num = 1; num <= 2000; num++) {
            documentsToCheck.add(DocumentRecord.create(type, currentYear, num));
        }
        
        processDocuments(documentsToCheck);
        log.info("✅ fetchCurrent terminé: {} documents vérifiés", documentsToCheck.size());
    }
    
    /**
     * Exécute le fetch pour les années précédentes (mode previous).
     */
    public void runPrevious(String type, int maxItems) {
        int currentYear = LocalDate.now().getYear();
        log.info("🔍 fetchPrevious: type={}, years=1960-{}", type, currentYear - 1);
        
        List<DocumentRecord> documentsToCheck = new ArrayList<>();
        int count = 0;
        
        // Générer documents des années précédentes
        for (int year = currentYear - 1; year >= 1960 && count < maxItems; year--) {
            for (int num = 1; num <= 2000 && count < maxItems; num++) {
                documentsToCheck.add(DocumentRecord.create(type, year, num));
                count++;
            }
        }
        
        processDocuments(documentsToCheck);
        log.info("✅ fetchPrevious terminé: {} documents vérifiés", documentsToCheck.size());
    }
    
    private void processDocuments(List<DocumentRecord> documents) {
        List<Future<DocumentRecord>> futures = new ArrayList<>();
        
        for (DocumentRecord doc : documents) {
            futures.add(executor.submit(() -> checkDocument(doc)));
        }
        
        int found = 0;
        int notFound = 0;
        
        for (Future<DocumentRecord> future : futures) {
            try {
                DocumentRecord result = future.get();
                if (result != null && result.getStatus() == ProcessingStatus.FETCHED) {
                    documentService.save(result);
                    found++;
                } else {
                    notFound++;
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("❌ Error processing document: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("📊 Résultats: {} trouvés, {} non trouvés", found, notFound);
    }
    
    private DocumentRecord checkDocument(DocumentRecord doc) {
        String url = String.format("%s/%s-%d-%d", 
            config.getBaseUrl(), doc.getType(), doc.getYear(), doc.getNumber());
        
        // Vérifier si déjà fetched
        if (documentService.exists(doc.getDocumentId())) {
            return null; // Skip, déjà traité
        }
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .header("User-Agent", config.getUserAgent())
                .timeout(Duration.ofMillis(config.getHttpTimeout()))
                .build();
            
            HttpResponse<Void> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.discarding());
            
            if (response.statusCode() == 200) {
                doc.setStatus(ProcessingStatus.FETCHED);
                log.debug("✅ Found: {}", doc.getDocumentId());
                return doc;
            } else {
                log.debug("❌ Not found ({}): {}", response.statusCode(), doc.getDocumentId());
                return null;
            }
            
        } catch (Exception e) {
            log.warn("⚠️ Error checking {}: {}", doc.getDocumentId(), e.getMessage());
            return null;
        }
    }
    
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
