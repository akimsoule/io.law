package bj.gouv.sgg.impl;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.exception.PromptLoadException;
import bj.gouv.sgg.modele.JsonResult;
import bj.gouv.sgg.model.LawDocument;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    void givenValidPromptFileWhenLoadPromptThenReturnsPromptContent() throws PromptLoadException {
        String prompt = ollamaClient.loadPrompt("pdf-parser.txt");
        
        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        assertTrue(prompt.contains("{text}") || prompt.length() > 100);
    }

    @Test
    void givenInvalidPromptFileWhenLoadPromptThenThrowsPromptLoadException() {
        assertThrows(PromptLoadException.class, () -> 
            ollamaClient.loadPrompt("non-existent-prompt.txt")
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
        
        JsonResult result = ollamaClient.transform(document, nonExistentPdf);
        
        assertNotNull(result);
        assertEquals(0.2, result.getConfidence());
        assertTrue(result.getSource().contains("FILE_NOT_FOUND"));
        assertTrue(result.getJson().contains("loi-2024-15"));
    }

    @Test
    void givenValidPdfAndOllamaResponseWhenTransformThenReturnsHighConfidenceResult() throws Exception {
        // Mock Ollama API response
        String mockResponse = "{\"model\":\"qwen2.5:7b\",\"response\":\"{\\\"documentId\\\":\\\"loi-2024-15\\\",\\\"type\\\":\\\"loi\\\",\\\"title\\\":\\\"Test Law\\\",\\\"articles\\\":[{\\\"number\\\":\\\"1\\\",\\\"content\\\":\\\"Article content\\\"}]}\"}";
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json"));

        // Use real PDF from test resources
        Path testPdf = Paths.get("src/test/resources/samples_pdf/test-simple-law.pdf");
        if (!Files.exists(testPdf)) {
            // Fallback: create minimal valid PDF for CI environments
            testPdf = tempDir.resolve("test.pdf");
            createMinimalValidPdf(testPdf);
        }

        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .ocrContent("Test OCR content with article")
            .build();

        JsonResult result = ollamaClient.transform(document, testPdf);

        assertNotNull(result);
        assertTrue(result.getConfidence() > 0.1, "Confidence should be > 0.1 but was " + result.getConfidence());
        assertTrue(result.getSource().contains("OLLAMA"));
        assertNotNull(result.getJson());
    }

    @Test
    void givenOllamaApiErrorWhenTransformThenReturnsLowConfidenceResult() throws Exception {
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
    void givenEmptyOcrContentWhenTransformThenProcessesWithDefaults() throws Exception {
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

    /**
     * Creates a minimal valid PDF for testing purposes.
     * This PDF can be successfully loaded by PDFBox.
     */
    private void createMinimalValidPdf(Path pdfPath) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText("Test PDF for Ollama Client");
                contentStream.endText();
            }
            
            document.save(pdfPath.toFile());
        }
    }
}
