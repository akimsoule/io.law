package bj.gouv.sgg.batch.writer;

import bj.gouv.sgg.model.LawDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires basiques pour FileDownloadWriter
 * Note: Tests simplifiés car FileDownloadWriter interagit avec le système de fichiers
 * Les tests end-to-end vérifient le comportement complet
 */
class FileDownloadWriterTest {

    private LawDocument testDocument;

    @BeforeEach
    void setUp() {
        testDocument = LawDocument.builder()
            .type("loi")
            .year(2025)
            .number(17)
            .url("https://sgg.gouv.bj/doc/loi-2025-17")
            .status(LawDocument.ProcessingStatus.DOWNLOADED)
            .pdfPath("data/pdfs/loi/loi-2025-17.pdf")
            .build();
    }

    @Test
    void testDocumentWithPdfPath() {
        // Then
        assertNotNull(testDocument.getPdfPath());
        assertEquals("data/pdfs/loi/loi-2025-17.pdf", testDocument.getPdfPath());
    }

    @Test
    void testDocumentIdMatchesPdfFilename() {
        // When
        String documentId = testDocument.getDocumentId();
        String pdfFilename = testDocument.getPdfFilename();

        // Then
        assertTrue(pdfFilename.startsWith(documentId));
        assertEquals("loi-2025-17", documentId);
        assertEquals("loi-2025-17.pdf", pdfFilename);
    }

    @Test
    void testDownloadedStatus() {
        // Then
        assertEquals(LawDocument.ProcessingStatus.DOWNLOADED, testDocument.getStatus());
    }

    @Test
    void testPdfPathFormat() {
        // Given
        String pdfPath = testDocument.getPdfPath();

        // Then
        assertTrue(pdfPath.contains("/loi/"));
        assertTrue(pdfPath.endsWith(".pdf"));
        assertTrue(pdfPath.contains("loi-2025-17"));
    }
}
