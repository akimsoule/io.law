package bj.gouv.sgg.batch;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.service.LawDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Writer pour sauvegarder les documents consolidés.
 * Thread-safe avec synchronized.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsolidateWriter implements ItemWriter<LawDocumentEntity> {

    private final LawDocumentService lawDocumentService;

    @Override
    public synchronized void write(Chunk<? extends LawDocumentEntity> chunk) {
        int consolidatedCount = 0;
        int errorCount = 0;

        for (LawDocumentEntity document : chunk.getItems()) {
            lawDocumentService.save(document);

            if (document.getStatus() == ProcessingStatus.CONSOLIDATED) {
                consolidatedCount++;
            } else if (document.getStatus() == ProcessingStatus.FAILED_CONSOLIDATION) {
                errorCount++;
                log.warn("⚠️ Consolidation échouée: {} (erreur: {})", 
                        document.getDocumentId(), document.getErrorMessage());
            }
        }

        if (consolidatedCount > 0) {
            log.info("💾 Sauvegardé {} documents consolidés", consolidatedCount);
        }
        if (errorCount > 0) {
            log.warn("⚠️ {} échecs de consolidation", errorCount);
        }
    }
}
