package bj.gouv.sgg.batch.processor;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.service.LawDocumentValidator;
import bj.gouv.sgg.service.PdfImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * ItemProcessor qui lance la conversion PDF -> images pour un document.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfImgProcessor implements ItemProcessor<LawDocumentEntity, LawDocumentEntity> {

    private final PdfImageService pdfImageService;
    private final LawDocumentValidator validator;

    @Override
    public LawDocumentEntity process(LawDocumentEntity document) throws Exception {
        log.info("🔄 Processing images for {}", document.getDocumentId());
        Path pdfPath;
        if (document.getPdfPath() != null && !document.getPdfPath().isEmpty()) {
            pdfPath = Path.of(document.getPdfPath());
        } else {
            pdfPath = validator.getPdfPath(document);
            document.setPdfPath(pdfPath.toString());
        }
        int pages = pdfImageService.convertPdfToImages(document.getDocumentId(), pdfPath);
        log.info("✅ Converted {} pages for {}", pages, document.getDocumentId());
        // No status change here - writer will persist any required updates
        return document;
    }
}
