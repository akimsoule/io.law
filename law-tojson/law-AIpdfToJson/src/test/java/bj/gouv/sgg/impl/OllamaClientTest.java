package bj.gouv.sgg.impl;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.exception.PromptLoadException;
import bj.gouv.sgg.modele.JsonResult;
import bj.gouv.sgg.model.LawDocument;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class OllamaClientTest {

    private MockWebServer mockServer;
    private OllamaClient ollamaClient;
    private LawProperties properties;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        properties = new LawProperties();
        LawProperties.Capacity capacity = new LawProperties.Capacity();
        capacity.setOllamaUrl(mockServer.url("/").toString().replaceAll("/$", ""));
        capacity.setOllamaModelsRequired("qwen2.5:7b");
        properties.setCapacity(capacity);

        ollamaClient = new OllamaClient(properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockServer != null) {
            mockServer.shutdown();
        }
    }

    @Test
    void testLoadPrompt_Success() throws PromptLoadException {
        String prompt = ollamaClient.loadPrompt("pdf-parser.txt");
        
        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains("{text}") || prompt.length() > 100);
    }

    @Test
    void testLoadPrompt_FileNotFound() {
        assertThrows(PromptLoadException.class, () -> 
            ollamaClient.loadPrompt("non-existent-prompt.txt")
        );
    }

    @Test
    void testTransform_PdfNotFound() throws IAException {
        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .build();
        
        Path nonExistentPdf = tempDir.resolve("non-existent.pdf");
        
        JsonResult result = ollamaClient.transform(document, nonExistentPdf);
        
        assertNotNull(result);
        assertEquals(0.2, result.getConfidence());
        assertTrue(result.getSource().contains("FILE_NOT_FOUND"));
        assertTrue(result.getJson().contains("loi-2024-15"));
    }

    @Test
    void testTransform_SuccessfulResponse() throws Exception {
        // Mock Ollama API response
        String mockResponse = "{\"model\":\"qwen2.5:7b\",\"response\":\"{\\\"documentId\\\":\\\"loi-2024-15\\\",\\\"type\\\":\\\"loi\\\",\\\"title\\\":\\\"Test Law\\\",\\\"articles\\\":[{\\\"number\\\":\\\"1\\\",\\\"content\\\":\\\"Article content\\\"}]}\"}";
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json"));

        // Create test PDF
        Path testPdf = tempDir.resolve("test.pdf");
        Files.writeString(testPdf, "%PDF-1.4 test content");

        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .ocrContent("Test OCR content with article")
            .build();

        JsonResult result = ollamaClient.transform(document, testPdf);

        assertNotNull(result);
        assertTrue(result.getConfidence() > 0.1);
        assertTrue(result.getSource().contains("OLLAMA"));
        assertNotNull(result.getJson());
    }

    @Test
    void testTransform_ApiError() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"));

        Path testPdf = tempDir.resolve("test.pdf");
        Files.writeString(testPdf, "%PDF-1.4 test");

        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .ocrContent("Test content")
            .build();

        JsonResult result = ollamaClient.transform(document, testPdf);

        assertNotNull(result);
        assertEquals(0.1, result.getConfidence());
        assertTrue(result.getSource().contains("ERROR"));
    }

    @Test
    void testTransform_EmptyOcrContent() throws Exception {
        String mockResponse = "{\"model\":\"qwen2.5:7b\",\"response\":\"{\\\"articles\\\":[]}\"}";
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(mockResponse));

        Path testPdf = tempDir.resolve("test.pdf");
        Files.writeString(testPdf, "%PDF-1.4");

        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .build();

        JsonResult result = ollamaClient.transform(document, testPdf);

        assertNotNull(result);
        assertTrue(result.getConfidence() >= 0.1);
    }

    @Test
    void testTransform_DecretType() throws Exception {
        String mockResponse = "{\"model\":\"qwen2.5:7b\",\"response\":\"{\\\"articles\\\":[]}\"}";
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(mockResponse));

        Path testPdf = tempDir.resolve("test.pdf");
        Files.writeString(testPdf, "%PDF-1.4");

        LawDocument document = LawDocument.builder()
            .type("decret")
            .year(2024)
            .number(100)
            .ocrContent("Decret content")
            .build();

        JsonResult result = ollamaClient.transform(document, testPdf);

        assertNotNull(result);
        // Verify decret-parser.txt is loaded (indirectly)
        assertNotNull(result.getJson());
    }
}
