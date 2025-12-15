package bj.gouv.sgg.batch.processor;

import bj.gouv.sgg.model.LawDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires basiques pour DownloadProcessor
 * Note: Tests simplifiés car DownloadProcessor télécharge via HTTP réel
 * Le filtrage des documents est géré par FetchedDocumentReader
 */
class DownloadProcessorTest {
    
    private LawDocument testDocument;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testDocument = LawDocument.builder()
            .type("loi")
            .year(2025)
            .number(17)
            .url("https://sgg.gouv.bj/doc/loi-2025-17")
            .status(LawDocument.ProcessingStatus.FETCHED)
            .build();
    }

    @Test
    void givenLawDocumentWhenGetIdThenFormattedCorrectly() {
        String documentId = testDocument.getDocumentId();
        assertEquals("loi-2025-17", documentId,
                "L'ID du document devrait être loi-2025-17");
    }

    @Test
    void givenDecretDocumentWhenGetIdThenFormattedCorrectly() {
        LawDocument decret = LawDocument.builder()
            .type("decret")
            .year(2025)
            .number(716)
            .status(LawDocument.ProcessingStatus.FETCHED)
            .build();

        String documentId = decret.getDocumentId();
        assertEquals("decret-2025-716", documentId,
                "L'ID du décret devrait être decret-2025-716");
    }

    @Test
    void givenTestDocument_whenGetUrl_thenReturnsCorrectUrl() {
        assertEquals("https://sgg.gouv.bj/doc/loi-2025-17", testDocument.getUrl());
    }

    @Test
    void givenTestDocument_whenGetPdfFilename_thenReturnsCorrectFilename() {
        String pdfFilename = testDocument.getPdfFilename();
        assertEquals("loi-2025-17.pdf", pdfFilename);
    }

    @Test
    void givenDocumentBuilder_whenBuild_thenCreatesValidDocument() {
        LawDocument doc = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .url("https://sgg.gouv.bj/doc/loi-2024-15")
            .status(LawDocument.ProcessingStatus.FETCHED)
            .build();

        assertNotNull(doc);
        assertEquals("loi", doc.getType());
        assertEquals(2024, doc.getYear());
        assertEquals(15, doc.getNumber());
        assertEquals("loi-2024-15", doc.getDocumentId());
        assertEquals("loi-2024-15.pdf", doc.getPdfFilename());
        assertEquals(LawDocument.ProcessingStatus.FETCHED, doc.getStatus());
    }

    @Test
    void givenTestDocument_whenGetStatus_thenReturnsFetched() {
        assertEquals(LawDocument.ProcessingStatus.FETCHED, testDocument.getStatus());
    }

    @Test
    void givenDownloadProcessor_whenSetForceMode_thenNoException() {
        DownloadProcessor processor = new DownloadProcessor();
        assertDoesNotThrow(() -> {
            processor.setForceMode(true);
            processor.setForceMode(false);
        });
    }
}
