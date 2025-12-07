package bj.gouv.sgg.transformer;

import bj.gouv.sgg.model.LawDocument;
import java.io.IOException;

public interface PdfToJsonTransformer {
    JsonResult transform(LawDocument document, java.nio.file.Path pdfPath) throws IOException;

    class JsonResult {
        private final String json;
        private final double confidence; // 0.0 - 1.0
        private final String source; // IA|OCR and provider detail

        public JsonResult(String json, double confidence, String source) {
            this.json = json;
            this.confidence = confidence;
            this.source = source;
        }
        public String getJson() { return json; }
        public double getConfidence() { return confidence; }
        public String getSource() { return source; }
    }
}
