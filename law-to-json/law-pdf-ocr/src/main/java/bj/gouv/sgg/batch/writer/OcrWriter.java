package bj.gouv.sgg.batch.writer;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.service.LawDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * ItemWriter Spring Batch pour sauvegarder les résultats OCR.
 * Met à jour la base de données avec ocrPath et status=OCRED.
 * Thread-safe avec synchronized pour éviter les conflits.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrWriter implements ItemWriter<LawDocumentEntity> {
    
    private final LawDocumentService lawDocumentService;
    
    @Override
    public synchronized void write(Chunk<? extends LawDocumentEntity> chunk) {
        int ocredCount = 0;
        int errorCount = 0;
        
        for (LawDocumentEntity document : chunk.getItems()) {
            try {
                lawDocumentService.save(document);
                
                if (document.getStatus() == ProcessingStatus.OCRED) {
                    ocredCount++;
                    log.debug("💾 Saved OCR: {}", document.getDocumentId());
                } else {
                    errorCount++;
                    log.debug("💾 Saved error: {} ({})", document.getDocumentId(), document.getStatus());
                }
                
            } catch (Exception e) {
                errorCount++;
                log.error("❌ Erreur sauvegarde {}: {}", document.getDocumentId(), e.getMessage());
            }
        }
        
        log.info("💾 Batch saved: {} documents ({} OCR completed, {} errors)", 
                 chunk.size(), ocredCount, errorCount);
    }
}
