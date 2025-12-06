package bj.gouv.sgg.batch.writer;

import bj.gouv.sgg.model.DownloadResult;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.DownloadResultRepository;
import bj.gouv.sgg.service.DocumentStatusManager;
import bj.gouv.sgg.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

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
    public void write(Chunk<? extends LawDocument> chunk) throws Exception {
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
        
        // Vérifier si déjà enregistré (idempotence), SAUF en mode force
        if (!forceMode && downloadResultRepository.existsByDocumentId(doc.getDocumentId())) {
            log.debug("⏭️ Already in download_results, skipping: {}", doc.getDocumentId());
            return true;
        }
        
        return false;
    }
    
    /**
     * Traite et sauvegarde un document.
     */
    private void processDocument(LawDocument doc) throws Exception {
        // En mode force, supprimer l'ancienne entrée avant de re-sauvegarder
        if (forceMode && downloadResultRepository.existsByDocumentId(doc.getDocumentId())) {
            downloadResultRepository.findByDocumentId(doc.getDocumentId())
                .ifPresent(existing -> {
                    downloadResultRepository.delete(existing);
                    downloadResultRepository.flush();
                    log.debug("🔄 Deleted old download_result for: {}", doc.getDocumentId());
                });
        }
        
        // Sauvegarder le PDF sur disque
        fileStorageService.savePdf(doc.getType(), doc.getDocumentId(), doc.getPdfContent());
        
        // Persister dans download_results
        DownloadResult downloadResult = DownloadResult.builder()
            .documentId(doc.getDocumentId())
            .url(doc.getUrl())
            .pdfPath(fileStorageService.pdfPath(doc.getType(), doc.getDocumentId()).toString())
            .sha256(doc.getSha256())
            .fileSize((long) doc.getPdfContent().length)
            .downloadedAt(LocalDateTime.now())
            .build();
        
        downloadResultRepository.save(downloadResult);
        
        // Mettre à jour le statut du document
        statusManager.updateStatus(doc.getDocumentId(), LawDocument.ProcessingStatus.DOWNLOADED);
        
        log.info("✅ PDF saved: {} ({} bytes)", doc.getDocumentId(), doc.getPdfContent().length);
    }
}
