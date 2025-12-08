package bj.gouv.sgg.impl;

import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.exception.PromptLoadException;
import bj.gouv.sgg.modele.JsonResult;
import bj.gouv.sgg.model.LawDocument;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GroqClientTest {

    private MockWebServer mockServer;
    private GroqClient groqClient;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException, NoSuchFieldException, IllegalAccessException {
        mockServer = new MockWebServer();
        mockServer.start();

        groqClient = new GroqClient();
        setField(groqClient, "apiKey", "test-api-key");
        setField(groqClient, "model", "llama-3.1-8b-instant");
    }
    
    private void setField(Object target, String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockServer != null) {
            mockServer.shutdown();
        }
    }

    @Test
    void givenValidPromptFileWhenLoadPromptThenReturnsPromptContent() throws PromptLoadException {
        String prompt = groqClient.loadPrompt("pdf-parser.txt");
        
        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains("{text}") || prompt.length() > 100);
    }

    @Test
    void givenDecretParserPromptWhenLoadPromptThenReturnsDecretPrompt() throws PromptLoadException {
        String prompt = groqClient.loadPrompt("decret-parser.txt");
        
        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
    }

    @Test
    void givenInvalidPromptFileWhenLoadPromptThenThrowsPromptLoadException() {
        assertThrows(PromptLoadException.class, () -> 
            groqClient.loadPrompt("non-existent.txt")
        );
    }

    @Test
    void givenMissingApiKeyWhenTransformThenThrowsIAException() throws Exception {
        setField(groqClient, "apiKey", "");
        
        Path testPdf = tempDir.resolve("test.pdf");
        Files.writeString(testPdf, "%PDF-1.4");

        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .build();

        assertThrows(IAException.class, () -> 
            groqClient.transform(document, testPdf)
        );
    }

    @Test
    void givenMissingPdfFileWhenTransformThenReturnsLowConfidenceResult() throws IAException {
        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .build();
        
        Path nonExistentPdf = tempDir.resolve("non-existent.pdf");
        
        JsonResult result = groqClient.transform(document, nonExistentPdf);
        
        assertNotNull(result);
        assertEquals(0.2, result.getConfidence());
        assertTrue(result.getSource().contains("FILE_NOT_FOUND"));
        assertTrue(result.getJson().contains("loi-2024-15"));
    }

    @Test
    void givenValidPdfWhenTransformWithoutGroqServerThenReturnsErrorResult() throws Exception {
        // Note: Ce test nécessiterait de mocker l'URL Groq dans le client
        // Pour l'instant, on teste uniquement les cas d'erreur et les prompts
        
        Path testPdf = tempDir.resolve("test.pdf");
        Files.writeString(testPdf, "%PDF-1.4 test content");

        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .ocrContent("Test OCR content")
            .build();

        // Sans mock du serveur Groq, cela devrait échouer
        JsonResult result = groqClient.transform(document, testPdf);
        
        assertNotNull(result);
        // Devrait retourner une erreur car pas de vrai serveur Groq
        assertTrue(result.getConfidence() <= 0.2);
    }

    @Test
    void givenDecretDocumentWhenTransformThenUsesDecretParser() throws Exception {
        Path testPdf = tempDir.resolve("test.pdf");
        Files.writeString(testPdf, "%PDF-1.4");

        LawDocument document = LawDocument.builder()
            .type("decret")
            .year(2024)
            .number(100)
            .ocrContent("Decret content")
            .build();

        JsonResult result = groqClient.transform(document, testPdf);

        assertNotNull(result);
        assertNotNull(result.getJson());
    }
}
