package bj.gouv.sgg.batch.writer;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.OtherProcessingStatus;
import bj.gouv.sgg.repository.LawDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ItemWriter persistant les entités traitées (ici on sauvegarde l'entité et marque IMAGED)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfImgWriter implements ItemWriter<LawDocumentEntity> {

    private final LawDocumentRepository repository;

    @Override
    @Transactional
    public synchronized void write(Chunk<? extends LawDocumentEntity> chunk) {
        for (LawDocumentEntity doc : chunk.getItems()) {
            // Marquer le document comme ayant des images générées
            doc.addOtherProcessingStatus(OtherProcessingStatus.IMAGED);
            repository.saveAndFlush(doc);
            log.debug("📦 PdfImgWriter saved {} (marked IMAGED)", doc.getDocumentId());
        }
    }
}
