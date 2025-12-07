package bj.gouv.sgg.processor;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.service.PdfToJsonService;
import bj.gouv.sgg.transformer.PdfToJsonTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.Optional;

/**
 * Génère un JSON d'articles à partir d'un PDF (IA par défaut, fallback OCR), idempotent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrJsonProcessor implements ItemProcessor<LawDocument, LawDocument> {

    private final PdfToJsonService pdfToJsonService;

    @Override
    public LawDocument process(LawDocument document) throws Exception {
        String pdfPath = document.getPdfPath();
        String fileName = java.nio.file.Paths.get(pdfPath).getFileName().toString();
        Optional<PdfToJsonTransformer.JsonResult> existing = readExistingJson(fileName);
        PdfToJsonTransformer.JsonResult result = pdfToJsonService.process(document, Paths.get(pdfPath), existing);
        saveJsonOutput(result.getJson(), fileName);
        log.info("JSON generated ({}): {}", result.getSource(), fileName);
        return document;
    }

    private Optional<PdfToJsonTransformer.JsonResult> readExistingJson(String pdfFileName) {
        try {
            String baseName = pdfFileName.replace(".pdf", "");
            java.nio.file.Path outputPath = java.nio.file.Paths.get("data/articles/loi", baseName + ".json");
            if (java.nio.file.Files.exists(outputPath)) {
                String json = java.nio.file.Files.readString(outputPath);
                return Optional.of(new PdfToJsonTransformer.JsonResult(json, 0.6, "EXISTING"));
            }
        } catch (java.io.IOException ignored) {
            // Pas de JSON existant lisible
        }
        return Optional.empty();
    }

    private void saveJsonOutput(String json, String pdfFileName) {
        try {
            String baseName = pdfFileName.replace(".pdf", "");
            java.nio.file.Path outputPath = java.nio.file.Paths.get("data/articles/loi", baseName + ".json");
            java.nio.file.Files.createDirectories(outputPath.getParent());
            java.nio.file.Files.writeString(outputPath, json);
        } catch (java.io.IOException e) {
            throw new bj.gouv.sgg.exception.JsonOutputException("Failed to save JSON output for: " + pdfFileName, e);
        }
    }
}
