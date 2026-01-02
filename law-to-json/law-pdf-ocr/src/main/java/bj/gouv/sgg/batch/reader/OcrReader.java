package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.repository.LawDocumentRepository;
import bj.gouv.sgg.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

/**
 * ItemReader Spring Batch pour lire les documents à traiter par OCR.
 * Scanne le disque pour découvrir les PDFs sans fichier OCR correspondant.
 * Thread-safe avec synchronized pour multi-threading.
 * 
 * @StepScope permet injection des paramètres du job (type, documentId, maxItems)
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class OcrReader implements ItemReader<LawDocumentEntity> {
    
    private final LawDocumentRepository repository;
    private final AppConfig config;
    
    @Value("#{jobParameters['type']}")
    private String type;
    
    @Value("#{jobParameters['documentId']}")
    private String documentId;
    
    @Value("#{jobParameters['maxItems'] ?: 1000}")
    private Long maxItems;
    
    private Queue<LawDocumentEntity> documents;
    
    @Override
    public synchronized LawDocumentEntity read() {
        if (documents == null) {
            initialize();
        }
        return documents.poll();
    }
    
    /**
     * Initialise la liste des documents à traiter par OCR.
     * Scanne le disque pour trouver les PDFs sans OCR.
     * Thread-safe avec double-check locking.
     */
    private synchronized void initialize() {
        // Double-check locking
        if (documents != null) {
            return;
        }
        
        this.documents = new ConcurrentLinkedQueue<>();
        
        try {
            if (documentId != null && !documentId.isEmpty() && !"ALL".equals(documentId)) {
                // Traiter un document spécifique
                processSpecificDocument(documentId);
            } else {
                // Scanner tous les PDFs du type
                scanPdfsForType(type);
            }
        } catch (IOException e) {
            log.error("❌ Erreur lors du scan du disque pour OCR", e);
        }
        
        log.info("📖 OcrReader initialisé: {} documents à traiter par OCR", documents.size());
    }
    
    private void processSpecificDocument(String docId) throws IOException {
        Path pdfDir = config.getStoragePath().resolve("pdfs").resolve(type);
        Path pdfFile = pdfDir.resolve(docId + ".pdf");
        
        if (Files.exists(pdfFile)) {
            repository.findByDocumentId(docId).ifPresent(doc -> {
                if (shouldProcess(doc)) {
                    documents.add(doc);
                }
            });
        } else {
            log.warn("⚠️ PDF non trouvé pour documentId={} : {}", docId, pdfFile);
        }
    }
    
    private void scanPdfsForType(String type) throws IOException {
        Path pdfDir = config.getStoragePath().resolve("pdfs").resolve(type);
        
        if (!Files.exists(pdfDir)) {
            log.warn("⚠️ Répertoire PDF non trouvé: {}", pdfDir);
            return;
        }

        log.info("🔍 Scanning PDFs in {} for OCR", pdfDir);
        
        try (Stream<Path> paths = Files.list(pdfDir)) {
            List<Path> pdfFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".pdf"))
                    .sorted() // Pour cohérence
                    .toList();

            log.info("🔎 Trouvé {} fichiers PDF pour type={}", pdfFiles.size(), type);

            int[] processedCount = {0};
            for (Path pdfFile : pdfFiles) {
                if (processedCount[0] >= maxItems) {
                    log.info("🔔 Reached maxItems limit ({}). Stopping scan.", maxItems);
                    break;
                }
                
                String fileName = pdfFile.getFileName().toString();
                String docId = fileName.substring(0, fileName.length() - 4); // Enlever .pdf
                
                repository.findByDocumentId(docId).ifPresentOrElse(doc -> {
                    if (shouldProcess(doc)) {
                        documents.add(doc);
                        processedCount[0]++;
                    }
                }, () -> {
                    log.debug("⏭️ Skip {} - document non trouvé en BD", docId);
                });
            }
        }
    }
    
    private boolean shouldProcess(LawDocumentEntity doc) {
        // Vérifier que le PDF existe (normalement oui puisque scanné)
        if (!Files.exists(config.getStoragePath().resolve("pdfs").resolve(doc.getType()).resolve(doc.getDocumentId() + ".pdf"))) {
            log.debug("⏭️ Skip {} - PDF non trouvé sur disque", doc.getDocumentId());
            return false;
        }
        
        // Vérifier que le document n'a pas déjà été OCRisé en BD
        if (doc.getStatus() == ProcessingStatus.OCRED_V2) {
            log.debug("⏭️ Skip {} - already OCRED_V2 in BD", doc.getDocumentId());
            return false;
        }
        
        // Vérifier que le fichier OCR n'existe pas
        Path ocrFile = config.getStoragePath().resolve("ocr").resolve(doc.getType()).resolve(doc.getDocumentId() + ".txt");
        if (Files.exists(ocrFile)) {
            log.debug("⏭️ Skip {} - OCR already exists: {}", doc.getDocumentId(), ocrFile);
            return false;
        }
        
        return true;
    }
}
