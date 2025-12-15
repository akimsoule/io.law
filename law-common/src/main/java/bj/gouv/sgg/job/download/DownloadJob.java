package bj.gouv.sgg.job.download;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.model.DocumentRecord;
import bj.gouv.sgg.model.ProcessingStatus;
import bj.gouv.sgg.service.DocumentService;
import bj.gouv.sgg.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.*;

/**
 * Job de téléchargement sans Spring Batch.
 * Télécharge les PDFs des documents FETCHED.
 */
@Slf4j
public class DownloadJob {
    
    private final AppConfig config;
    private final DocumentService documentService;
    private final FileStorageService fileStorageService;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    
    public DownloadJob() {
        this.config = AppConfig.get();
        this.documentService = new DocumentService();
        this.fileStorageService = new FileStorageService();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getHttpTimeout()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.executor = Executors.newFixedThreadPool(config.getMaxThreads());
    }
    
    public void run(String type, int maxDocuments) {
        log.info("⬇️  downloadJob: type={}, max={}", type, maxDocuments);
        
        // Récupérer documents FETCHED
        List<DocumentRecord> documents = documentService.findByTypeAndStatus(type, ProcessingStatus.FETCHED)
            .stream()
            .limit(maxDocuments > 0 ? maxDocuments : Long.MAX_VALUE)
            .toList();
        
        log.info("📋 {} documents à télécharger", documents.size());
        
        List<Future<DocumentRecord>> futures = new ArrayList<>();
        for (DocumentRecord doc : documents) {
            futures.add(executor.submit(() -> downloadDocument(doc)));
        }
        
        int success = 0;
        int failed = 0;
        
        for (Future<DocumentRecord> future : futures) {
            try {
                DocumentRecord result = future.get();
                if (result != null) {
                    documentService.save(result);
                    if (result.getStatus() == ProcessingStatus.DOWNLOADED) {
                        success++;
                    } else {
                        failed++;
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("❌ Error downloading: {}", e.getMessage());
                failed++;
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("✅ downloadJob terminé: {} réussis, {} échecs", success, failed);
    }
    
    private DocumentRecord downloadDocument(DocumentRecord doc) {
        String url = String.format("%s/%s-%d-%d", 
            config.getBaseUrl(), doc.getType(), doc.getYear(), doc.getNumber());
        
        try {
            // Vérifier si déjà téléchargé
            Path pdfPath = fileStorageService.pdfPath(doc.getType(), doc.getDocumentId());
            if (Files.exists(pdfPath)) {
                log.debug("⏭️ Already downloaded: {}", doc.getDocumentId());
                doc.setStatus(ProcessingStatus.DOWNLOADED);
                doc.setPdfPath(pdfPath.toString());
                return doc;
            }
            
            // Télécharger
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("User-Agent", config.getUserAgent())
                .timeout(Duration.ofMillis(config.getHttpTimeout()))
                .build();
            
            HttpResponse<byte[]> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofByteArray());
            
            if (response.statusCode() == 200) {
                byte[] data = response.body();
                
                // Calculer SHA-256
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(data);
                String sha256 = HexFormat.of().formatHex(hash);
                
                // Sauvegarder
                Files.createDirectories(pdfPath.getParent());
                Files.write(pdfPath, data);
                
                doc.setStatus(ProcessingStatus.DOWNLOADED);
                doc.setPdfPath(pdfPath.toString());
                
                log.info("✅ Downloaded: {} ({})", doc.getDocumentId(), sha256.substring(0, 8));
                return doc;
                
            } else {
                log.warn("❌ Failed to download {} : HTTP {}", doc.getDocumentId(), response.statusCode());
                doc.setStatus(ProcessingStatus.FAILED);
                doc.setErrorMessage("HTTP " + response.statusCode());
                return doc;
            }
            
        } catch (Exception e) {
            log.error("❌ Error downloading {}: {}", doc.getDocumentId(), e.getMessage());
            doc.setStatus(ProcessingStatus.FAILED);
            doc.setErrorMessage(e.getMessage());
            return doc;
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
