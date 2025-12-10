package bj.gouv.sgg.fix.batch;

import bj.gouv.sgg.model.LawDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Writer qui ne fait rien - les corrections sont déjà appliquées par le processor.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FixWriter implements ItemWriter<LawDocument> {
    
    @Override
    public void write(@NonNull Chunk<? extends LawDocument> chunk) {
        // Les corrections sont déjà appliquées dans FixProcessor
        // Ce writer sert juste à compléter le job Spring Batch
        log.debug("📝 Chunk de {} documents traités", chunk.size());
    }
}
