package bj.gouv.sgg.batch.writer;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.service.LawDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Writer Spring Batch pour extraction JSON.
 * 
 * <p>Sauvegarde les documents mis à jour en base de données.
 * Thread-safe avec synchronized.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OcrJsonWriter implements ItemWriter<LawDocumentEntity> {

    private final LawDocumentService lawDocumentService;

    private int jsonExtractedCount = 0;
    private int errorCount = 0;

    @Override
    public synchronized void write(Chunk<? extends LawDocumentEntity> chunk) {
        for (LawDocumentEntity document : chunk.getItems()) {
            try {
                lawDocumentService.save(document);
                
                switch (document.getStatus()) {
                    case EXTRACTED:
                        jsonExtractedCount++;
                        log.debug("💾 Saved EXTRACTED: {}", document.getDocumentId());
                        break;
                    case FAILED_EXTRACTION:
                        errorCount++;
                        log.warn("⚠️ Saved FAILED_EXTRACTION: {} - {}", 
                                document.getDocumentId(), document.getErrorMessage());
                        break;
                    default:
                        log.warn("⚠️ Unexpected status: {} for {}", 
                                document.getStatus(), document.getDocumentId());
                }
            } catch (Exception e) {
                log.error("❌ Error saving document {}: {}", 
                        document.getDocumentId(), e.getMessage(), e);
                errorCount++;
            }
        }
        
        log.info("📊 Progress: {} JSON extracted, {} errors", jsonExtractedCount, errorCount);
    }
}
