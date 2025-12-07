package bj.gouv.sgg.integration;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.impl.NoClient;
import bj.gouv.sgg.impl.OllamaClient;
import bj.gouv.sgg.modele.JsonResult;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.service.IAService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests d'intégration pour IAService
 */
class IAServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private IAService noClient;
    private IAService ollamaClient;

    @BeforeEach
    void setUp() {
        noClient = new NoClient();
        
        LawProperties properties = new LawProperties();
        LawProperties.Capacity capacity = new LawProperties.Capacity();
        capacity.setOllamaUrl("http://localhost:11434");
        capacity.setOllamaModelsRequired("qwen2.5:7b");
        properties.setCapacity(capacity);
        
        ollamaClient = new OllamaClient(properties);
    }

    @Test
    void testNoClientFallback() {
        assertNotNull(noClient);
        assertTrue(noClient instanceof NoClient);
    }

    @Test
    void testPromptLoading() throws Exception {
        // Test avec NoClient (devrait échouer)
        assertThrows(Exception.class, () -> noClient.loadPrompt("pdf-parser.txt"));
        
        // Test avec OllamaClient (devrait réussir)
        String prompt = ollamaClient.loadPrompt("pdf-parser.txt");
        assertNotNull(prompt);
        assertFalse(prompt.isEmpty());
    }

    @Test
    void testTransformWithMissingPdf() throws Exception {
        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .build();
        
        Path missingPdf = tempDir.resolve("missing.pdf");
        
        // NoClient devrait lancer exception
        assertThrows(Exception.class, () -> noClient.transform(document, missingPdf));
        
        // OllamaClient devrait retourner résultat avec faible confiance
        JsonResult result = ollamaClient.transform(document, missingPdf);
        assertNotNull(result);
        assertTrue(result.getConfidence() < 0.5);
    }

    @Test
    void testTransformWithValidPdf() throws Exception {
        Path testPdf = tempDir.resolve("test.pdf");
        Files.writeString(testPdf, "%PDF-1.4\nTest content");
        
        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .ocrContent("Article 1: Test content")
            .build();
        
        // NoClient devrait échouer
        assertThrows(Exception.class, () -> noClient.transform(document, testPdf));
        
        // OllamaClient devrait traiter (avec ou sans succès selon disponibilité Ollama)
        JsonResult result = ollamaClient.transform(document, testPdf);
        assertNotNull(result);
        assertNotNull(result.getJson());
        assertTrue(result.getConfidence() >= 0.1);
    }

    @Test
    void testDecretTypeDocuments() throws Exception {
        Path testPdf = tempDir.resolve("decret.pdf");
        Files.writeString(testPdf, "%PDF-1.4\nDecret content");
        
        LawDocument document = LawDocument.builder()
            .type("decret")
            .year(2024)
            .number(100)
            .ocrContent("Décret portant...")
            .build();
        
        JsonResult result = ollamaClient.transform(document, testPdf);
        assertNotNull(result);
        // Devrait utiliser decret-parser.txt
    }
}
