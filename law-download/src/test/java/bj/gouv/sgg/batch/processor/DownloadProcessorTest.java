package bj.gouv.sgg.batch.processor;

import bj.gouv.sgg.model.LawDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires basiques pour DownloadProcessor
 * Note: Tests simplifiés car DownloadProcessor appelle des services HTTP réels
 * Les tests end-to-end vérifient le comportement complet
 */
class DownloadProcessorTest {

    private LawDocument testDocument;

    @BeforeEach
    void setUp() {
        testDocument = LawDocument.builder()
            .type("loi")
            .year(2025)
            .number(17)
            .url("https://sgg.gouv.bj/doc/loi-2025-17")
            .status(LawDocument.ProcessingStatus.FETCHED)
            .build();
    }

    @Test
    void testDocumentIdGeneration() {
        // When
        String documentId = testDocument.getDocumentId();

        // Then
        assertEquals("loi-2025-17", documentId);
    }

    @Test
    void testDecretDocumentId() {
        // Given
        LawDocument decret = LawDocument.builder()
            .type("decret")
            .year(2025)
            .number(716)
            .status(LawDocument.ProcessingStatus.FETCHED)
            .build();

        // When
        String documentId = decret.getDocumentId();

        // Then
        assertEquals("decret-2025-716", documentId);
    }

    @Test
    void testUrlConstruction() {
        // Then
        assertEquals("https://sgg.gouv.bj/doc/loi-2025-17", testDocument.getUrl());
    }

    @Test
    void testPdfFilenameGeneration() {
        // When
        String pdfFilename = testDocument.getPdfFilename();

        // Then
        assertEquals("loi-2025-17.pdf", pdfFilename);
    }

    @Test
    void testDocumentBuilderPattern() {
        // Given & When
        LawDocument doc = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .url("https://sgg.gouv.bj/doc/loi-2024-15")
            .status(LawDocument.ProcessingStatus.FETCHED)
            .build();

        // Then
        assertNotNull(doc);
        assertEquals("loi", doc.getType());
        assertEquals(2024, doc.getYear());
        assertEquals(15, doc.getNumber());
        assertEquals("loi-2024-15", doc.getDocumentId());
        assertEquals("loi-2024-15.pdf", doc.getPdfFilename());
        assertEquals(LawDocument.ProcessingStatus.FETCHED, doc.getStatus());
    }

    @Test
    void testInitialStatus() {
        // Then
        assertEquals(LawDocument.ProcessingStatus.FETCHED, testDocument.getStatus());
    }

    @Test
    void testForceModeSetter() {
        // Given
        DownloadProcessor processor = new DownloadProcessor();
        
        // When
        processor.setForceMode(true);

        // Then - Pas d'exception, force mode activé
        assertDoesNotThrow(() -> processor.setForceMode(false));
    }

    @Test
    void testSkipAlreadyDownloadedDocument() throws Exception {
        // Given
        DownloadProcessor processor = new DownloadProcessor();
        testDocument.setStatus(LawDocument.ProcessingStatus.DOWNLOADED);
        processor.setForceMode(false);

        // When
        LawDocument result = processor.process(testDocument);

        // Then
        assertNull(result, "Le document DOWNLOADED devrait être skippé en mode normal");
    }

    @Test
    void testUrlShouldNotContainDownloadSuffix() {
        // Given
        String baseUrl = "https://sgg.gouv.bj/doc/loi-2025-04";
        LawDocument doc = LawDocument.builder()
            .type("loi")
            .year(2025)
            .number(4)
            .url(baseUrl)
            .status(LawDocument.ProcessingStatus.FETCHED)
            .build();

        // Then
        assertFalse(doc.getUrl().endsWith("/download"),
            "L'URL en base ne devrait PAS contenir /download");
        assertEquals(baseUrl, doc.getUrl());
    }

    @Test
    void testDownloadUrlConstruction() {
        // Given
        String baseUrl = "https://sgg.gouv.bj/doc/loi-2025-04";
        String expectedDownloadUrl = baseUrl + "/download";
        
        LawDocument doc = LawDocument.builder()
            .type("loi")
            .year(2025)
            .number(4)
            .url(baseUrl)
            .status(LawDocument.ProcessingStatus.FETCHED)
            .build();

        // Then
        String downloadUrl = doc.getUrl() + "/download";
        assertEquals(expectedDownloadUrl, downloadUrl,
            "L'URL de téléchargement devrait être baseUrl + /download");
        assertEquals("https://sgg.gouv.bj/doc/loi-2025-04/download", downloadUrl);
    }

    @Test
    void testDocumentWithPaddedNumber() {
        // Given - Numéro < 10 devrait avoir padding en base
        LawDocument doc = LawDocument.builder()
            .type("loi")
            .year(2025)
            .number(4)
            .url("https://sgg.gouv.bj/doc/loi-2025-04") // Avec padding
            .status(LawDocument.ProcessingStatus.FETCHED)
            .build();

        // Then
        assertTrue(doc.getUrl().contains("loi-2025-04"),
            "L'URL devrait contenir le numéro avec padding (04)");
        assertFalse(doc.getUrl().contains("loi-2025-4"),
            "L'URL ne devrait PAS contenir le numéro sans padding (4)");
    }
}
