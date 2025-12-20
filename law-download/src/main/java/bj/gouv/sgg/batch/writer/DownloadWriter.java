package bj.gouv.sgg.batch.writer;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.service.LawDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * ItemWriter Spring Batch pour sauvegarder les documents téléchargés.
 * Met à jour les documents en base avec pdfPath, pdfHash et nouveau status.
 * 
 * Thread-safe : synchronized pour éviter les conflits d'écriture concurrente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DownloadWriter implements ItemWriter<LawDocumentEntity> {
    
    private final LawDocumentService lawDocumentService;
    
    @Override
    public synchronized void write(Chunk<? extends LawDocumentEntity> chunk) {
        for (LawDocumentEntity document : chunk.getItems()) {
            try {
                lawDocumentService.save(document);
                log.debug("💾 Saved: {} (status={})", document.getDocumentId(), document.getStatus());
            } catch (Exception e) {
                log.error("❌ Erreur sauvegarde {}: {}", document.getDocumentId(), e.getMessage());
            }
        }
        
        if (!chunk.isEmpty()) {
            long downloaded = chunk.getItems().stream()
                .filter(d -> d.getStatus().name().equals("DOWNLOADED"))
                .count();
            long errors = chunk.size() - downloaded;
            
            log.info("💾 Batch saved: {} documents ({} downloaded, {} errors)", 
                     chunk.size(), downloaded, errors);
        }
    }
}
