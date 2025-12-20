package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.repository.LawDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ItemReader Spring Batch pour lire les documents à traiter par OCR.
 * Lit les documents avec status DOWNLOADED ou FAILED_OCR.
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
     * Thread-safe avec double-check locking.
     */
    private synchronized void initialize() {
        // Double-check locking
        if (documents != null) {
            return;
        }
        
        this.documents = new ConcurrentLinkedQueue<>();
        
        // Mode document spécifique (pas "ALL")
        if (documentId != null && !documentId.isEmpty() && !"ALL".equals(documentId)) {
            repository.findByDocumentId(documentId).ifPresent(doc -> {
                if (doc.getStatus() == ProcessingStatus.DOWNLOADED || 
                    doc.getStatus() == ProcessingStatus.FAILED_OCR) {
                    documents.add(doc);
                    log.info("📖 OcrReader initialisé pour document spécifique: {}", documentId);
                } else {
                    log.warn("⚠️ Document {} status={}, skip OCR", documentId, doc.getStatus());
                }
            });
            return;
        }
        
        // Mode type (tous les documents DOWNLOADED d'un type)
        List<LawDocumentEntity> found = repository.findByTypeAndStatusIn(
            type, 
            List.of(ProcessingStatus.DOWNLOADED, ProcessingStatus.FAILED_OCR)
        );
        
        // Limiter selon maxItems
        int count = 0;
        for (LawDocumentEntity doc : found) {
            if (count >= maxItems) {
                break;
            }
            // Vérifier si pas déjà traité par OCR (idempotence)
            if (doc.getOcrPath() == null || doc.getOcrPath().isEmpty()) {
                documents.add(doc);
                count++;
            }
        }
        
        log.info("📖 OcrReader initialisé: {} documents à traiter par OCR (type={}, maxItems={})", 
                 documents.size(), type, maxItems);
    }
}
