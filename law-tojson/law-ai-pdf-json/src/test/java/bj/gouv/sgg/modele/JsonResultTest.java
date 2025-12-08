package bj.gouv.sgg.modele;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonResultTest {

    @Test
    void givenJsonDataWhenCreateJsonResultThenSetsAllFields() {
        String json = "{\"documentId\":\"loi-2024-15\",\"articles\":[]}";
        double confidence = 0.85;
        String source = "IA:OLLAMA";

        JsonResult result = new JsonResult(json, confidence, source);

        assertEquals(json, result.getJson());
        assertEquals(confidence, result.getConfidence());
        assertEquals(source, result.getSource());
    }

    @Test
    void givenDifferentSourcesWhenCreateResultsThenStoresCorrectSource() {
        JsonResult ollamaResult = new JsonResult("{}", 0.9, "IA:OLLAMA");
        assertEquals("IA:OLLAMA", ollamaResult.getSource());

        JsonResult groqResult = new JsonResult("{}", 0.85, "IA:GROQ");
        assertEquals("IA:GROQ", groqResult.getSource());

        JsonResult errorResult = new JsonResult("{}", 0.1, "IA:OLLAMA:ERROR");
        assertEquals("IA:OLLAMA:ERROR", errorResult.getSource());
    }

    @Test
    void givenConfidenceValuesWhenCreateResultsThenRemainsInValidRange() {
        JsonResult lowConfidence = new JsonResult("{}", 0.15, "OCR");
        assertTrue(lowConfidence.getConfidence() >= 0.0);
        assertTrue(lowConfidence.getConfidence() <= 1.0);

        JsonResult highConfidence = new JsonResult("{}", 0.95, "IA:OLLAMA");
        assertTrue(highConfidence.getConfidence() >= 0.0);
        assertTrue(highConfidence.getConfidence() <= 1.0);
    }

    @Test
    void givenEmptyArticlesWhenCreateResultThenStoresEmptyJson() {
        JsonResult emptyResult = new JsonResult("{\"articles\":[]}", 0.2, "IA:OLLAMA");
        assertNotNull(emptyResult.getJson());
        assertFalse(emptyResult.getJson().isEmpty());
    }

    @Test
    void givenLargeArticleListWhenCreateResultThenStoresCompleteJson() {
        StringBuilder largeJson = new StringBuilder("{\"articles\":[");
        for (int i = 0; i < 100; i++) {
            if (i > 0) largeJson.append(",");
            largeJson.append("{\"number\":\"").append(i).append("\",\"content\":\"Article ").append(i).append("\"}");
        }
        largeJson.append("]}");

        JsonResult result = new JsonResult(largeJson.toString(), 0.9, "IA:GROQ");
        
        assertNotNull(result.getJson());
        assertTrue(result.getJson().length() > 1000);
        assertEquals(0.9, result.getConfidence());
    }
}
