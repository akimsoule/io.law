package bj.gouv.sgg.batch.writer;

import bj.gouv.sgg.model.DownloadResult;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.DownloadResultRepository;
import bj.gouv.sgg.service.DocumentStatusManager;
import bj.gouv.sgg.service.DownloadResultUpdateService;
import bj.gouv.sgg.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Writer qui enregistre les PDFs sur disque, persiste dans download_results et met à jour le statut.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileDownloadWriter implements ItemWriter<LawDocument> {

    private final FileStorageService fileStorageService;
    private final DocumentStatusManager statusManager;
    private final DownloadResultUpdateService downloadResultUpdateService;
    
    // Gardé pour la vérification d'existence (read-only)
    private final DownloadResultRepository downloadResultRepository;
    
    private boolean forceMode = false;
    
    /**
     * Active le mode force (re-sauvegarde même si déjà dans download_results)
     */
    public void setForceMode(boolean force) {
        this.forceMode = force;
        log.debug("Writer force mode set: {}", force);
    }

    @Override
    public void write(Chunk<? extends LawDocument> chunk) throws IOException {
        int saved = 0;
        int skipped = 0;
        
        for (LawDocument doc : chunk) {
            if (shouldSkipDocument(doc)) {
                skipped++;
            } else {
                processDocument(doc);
                saved++;
            }
        }
        
        log.info("📊 DownloadWriter: saved={} skipped={}", saved, skipped);
    }
    
    /**
     * Vérifie si un document doit être skippé.
     */
    private boolean shouldSkipDocument(LawDocument doc) {
        if (doc == null || doc.getPdfContent() == null || doc.getPdfContent().length == 0) {
            return true;
        }
        
        String docId = doc.getDocumentId();
        boolean existsInDb = downloadResultRepository.existsByDocumentId(docId);
        boolean fileExists = fileStorageService.pdfExists(doc.getType(), docId);
        
        // Skip si déjà en base ET fichier présent, SAUF en mode force
        if (!forceMode && existsInDb && fileExists) {
            log.debug("⏭️ [{}] Already in DB and file exists, skipping", docId);
            return true;
        }
        
        // Si en base mais fichier manquant, on ne skip pas (re-sauvegarde)
        if (existsInDb && !fileExists) {
            log.info("💾 [{}] In DB but PDF missing on disk → re-saving file", docId);
        }
        
        return false;
    }
    
    /**
     * Traite et sauvegarde un document.
     * Utilise DownloadResultUpdateService pour éviter les deadlocks en multi-threading.
     */
    private void processDocument(LawDocument doc) throws IOException {
        String docId = doc.getDocumentId();
        
        // Sauvegarder le PDF sur disque
        fileStorageService.savePdf(doc.getType(), docId, doc.getPdfContent());
        
        // Sauvegarder le résultat dans download_results avec transaction isolée
        String pdfPath = fileStorageService.pdfPath(doc.getType(), docId).toString();
        downloadResultUpdateService.saveDownloadResult(
            docId,
            doc.getUrl(),
            pdfPath,
            doc.getSha256(),
            (long) doc.getPdfContent().length
        );
        
        // Mettre à jour le statut du document (transaction séparée via @Transactional)
        statusManager.updateStatus(doc.getDocumentId(), LawDocument.ProcessingStatus.DOWNLOADED);
        
        log.info("✅ PDF saved: {} ({} bytes)", doc.getDocumentId(), doc.getPdfContent().length);
    }
}
