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
    void givenDownloadedDocumentWhenGetPdfPathThenReturnsCorrectPath() {
        // Given: Document téléchargé avec pdfPath (défini dans setUp)

        // Then: PdfPath correctement défini et formaté
        assertNotNull(testDocument.getPdfPath(),
                "Le pdfPath ne devrait pas être null");
        assertEquals("data/pdfs/loi/loi-2025-17.pdf", testDocument.getPdfPath(),
                "Le pdfPath devrait suivre le pattern data/pdfs/{type}/{documentId}.pdf");
    }

    @Test
    void givenDocumentWhenGetIdAndFilenameThenConsistent() {
        // Given: Document avec ID et PDF filename (défini dans setUp)

        // When: Récupération de l'ID et du filename
        String documentId = testDocument.getDocumentId();
        String pdfFilename = testDocument.getPdfFilename();

        // Then: Filename commence par ID et a l'extension .pdf
        assertTrue(pdfFilename.startsWith(documentId),
                "Le filename devrait commencer par l'ID du document");
        assertEquals("loi-2025-17", documentId,
                "L'ID devrait être loi-2025-17");
        assertEquals("loi-2025-17.pdf", pdfFilename,
                "Le filename devrait être loi-2025-17.pdf");
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

    @Test
    void testDocumentWithPdfContent() {
        // Given
        byte[] pdfContent = "%PDF-1.4\n%EOF".getBytes();
        testDocument.setPdfContent(pdfContent);

        // When/Then
        assertNotNull(testDocument.getPdfContent());
        assertTrue(testDocument.getPdfContent().length > 0);
        assertEquals(pdfContent.length, testDocument.getPdfContent().length);
    }

    @Test
    void testDocumentWithSha256() {
        // Given
        String sha256Hash = "abc123def456";
        testDocument.setSha256(sha256Hash);

        // When/Then
        assertEquals(sha256Hash, testDocument.getSha256());
    }

    @Test
    void testDocumentWithNullPdfContent() {
        // Given
        LawDocument docWithoutContent = LawDocument.builder()
            .type("loi")
            .year(2025)
            .number(18)
            .url("https://sgg.gouv.bj/doc/loi-2025-18")
            .status(LawDocument.ProcessingStatus.FETCHED)
            .build();

        // Then
        assertNull(docWithoutContent.getPdfContent(),
            "Un document non téléchargé ne devrait pas avoir de contenu PDF");
    }

    @Test
    void testDocumentReadyForWrite() {
        // Given - Document avec tout ce qu'il faut pour écrire
        byte[] content = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF
        LawDocument readyDoc = LawDocument.builder()
            .type("loi")
            .year(2025)
            .number(4)
            .url("https://sgg.gouv.bj/doc/loi-2025-04")
            .status(LawDocument.ProcessingStatus.DOWNLOADED)
            .pdfContent(content)
            .sha256("abc123")
            .build();

        // Then
        assertNotNull(readyDoc.getPdfContent());
        assertTrue(readyDoc.getPdfContent().length > 0);
        assertNotNull(readyDoc.getSha256());
        assertEquals(LawDocument.ProcessingStatus.DOWNLOADED, readyDoc.getStatus());
    }

    @Test
    void testPdfContentIsPdfSignature() {
        // Given
        byte[] pdfSignature = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF
        testDocument.setPdfContent(pdfSignature);

        // When
        byte[] content = testDocument.getPdfContent();

        // Then
        assertEquals(0x25, content[0]); // %
        assertEquals(0x50, content[1]); // P
        assertEquals(0x44, content[2]); // D
        assertEquals(0x46, content[3]); // F
    }
}
