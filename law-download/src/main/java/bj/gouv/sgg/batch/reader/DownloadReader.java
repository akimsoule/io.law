package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.repository.LawDocumentRepository;
import bj.gouv.sgg.service.LawDocumentValidator;
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
 * ItemReader Spring Batch pour lire les documents à télécharger.
 * Lit les documents avec status FETCHED ou CORRUPTED.
 * Thread-safe avec synchronized pour multi-threading.
 * 
 * @StepScope permet injection des paramètres du job (type, documentId, maxItems)
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class DownloadReader implements ItemReader<LawDocumentEntity> {
    
    private final LawDocumentRepository repository;
    private final LawDocumentValidator validator;
    
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
     * Initialise la liste des documents à télécharger.
     * Thread-safe avec double-check locking.
     */
    private synchronized void initialize() {
        // Double-check locking
        if (documents != null) {
            return;
        }
        
        log.info("🔍 DownloadReader - Initialisation...");
        log.info("   Type: {}", type);
        log.info("   DocumentId: {}", documentId);
        log.info("   MaxItems: {}", maxItems);
        
        this.documents = new ConcurrentLinkedQueue<>();
        
        // Mode document spécifique (pas "ALL")
        if (documentId != null && !documentId.isEmpty() && !"ALL".equals(documentId)) {
            log.info("🔍 Mode: Document spécifique ({})", documentId);
            repository.findByDocumentId(documentId).ifPresentOrElse(
                doc -> {
                    log.info("   Trouvé: {} - Status: {}, PdfPath: {}", 
                             doc.getDocumentId(), doc.getStatus(), doc.getPdfPath());
                    if (validator.mustDownload(doc)) {
                        documents.add(doc);
                        log.info("✅ Document ajouté à la queue (validator.mustDownload=true)");
                    } else {
                        log.warn("⚠️ Validator: mustDownload=false, skip download");
                    }
                },
                () -> log.warn("❌ Document {} non trouvé en base", documentId)
            );
            log.info("📖 DownloadReader initialisé: {} document(s)", documents.size());
            return;
        }
        
        // Mode type (tous les documents d'un type)
        log.info("🔍 Mode: Tous les documents type={}", type);
        log.info("🔍 Utilisation de validator.mustDownload() pour filtrer");
        
        List<LawDocumentEntity> found = repository.findByType(type);
        
        log.info("🔍 Trouvé {} document(s) en base pour type={}", found.size(), type);
        
        // Filtrer avec validator.mustDownload() et limiter selon maxItems
        int count = 0;
        int skippedByValidator = 0;
        for (LawDocumentEntity doc : found) {
            if (count >= maxItems) {
                log.info("⚠️ Limite maxItems={} atteinte, arrêt", maxItems);
                break;
            }
            
            if (validator.mustDownload(doc)) {
                documents.add(doc);
                count++;
                log.debug("   ✅ Ajouté: {} (status={}, validator.mustDownload=true)", 
                         doc.getDocumentId(), doc.getStatus());
            } else {
                skippedByValidator++;
                log.debug("   ⏭️  Skip: {} (status={}, validator.mustDownload=false)", 
                         doc.getDocumentId(), doc.getStatus());
            }
        }
        
        log.info("📊 Résultat:");
        log.info("   Total trouvé: {}", found.size());
        log.info("   Skipped (validator): {}", skippedByValidator);
        log.info("   À télécharger: {}", documents.size());
        log.info("📖 DownloadReader initialisé: {} documents à télécharger (type={}, maxItems={})", 
                 documents.size(), type, maxItems);
    }
}
