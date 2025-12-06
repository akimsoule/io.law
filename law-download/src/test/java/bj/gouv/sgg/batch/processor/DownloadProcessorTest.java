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
}
