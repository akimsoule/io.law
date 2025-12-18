package bj.gouv.sgg.batch.writer;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.service.LawDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * ItemWriter Spring Batch pour persister les documents trouvés.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FetchDocumentWriter implements ItemWriter<LawDocumentEntity> {
    
    private final LawDocumentService lawDocumentService;
    
    @Override
    public synchronized void write(Chunk<? extends LawDocumentEntity> chunk) {
        for (LawDocumentEntity document : chunk.getItems()) {
            try {
                lawDocumentService.save(document);
                log.debug("💾 Saved: {}", document.getDocumentId());
            } catch (Exception e) {
                log.error("❌ Erreur sauvegarde {}: {}", document.getDocumentId(), e.getMessage());
            }
        }
        
        if (!chunk.isEmpty()) {
            log.info("💾 Batch saved: {} documents", chunk.size());
        }
    }
}
