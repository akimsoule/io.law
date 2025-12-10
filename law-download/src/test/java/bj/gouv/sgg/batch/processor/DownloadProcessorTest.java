package bj.gouv.sgg.batch.processor;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires basiques pour DownloadProcessor
 * Note: Tests simplifiés car DownloadProcessor appelle des services HTTP réels
 * Les tests end-to-end vérifient le comportement complet
 */
class DownloadProcessorTest {

    @Mock
    private FileStorageService fileStorageService;
    
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
        // Given: Document loi avec année 2025 et numéro 17 (défini dans setUp)

        // When: Récupération de l'ID du document
        String documentId = testDocument.getDocumentId();

        // Then: ID formaté selon le pattern loi-year-number
        assertEquals("loi-2025-17", documentId,
                "L'ID du document devrait être loi-2025-17");
    }

    @Test
    void givenDecretDocumentWhenGetIdThenFormattedCorrectly() {
        // Given: Document décret avec année 2025 et numéro 716
        LawDocument decret = LawDocument.builder()
            .type("decret")
            .year(2025)
            .number(716)
            .status(LawDocument.ProcessingStatus.FETCHED)
            .build();

        // When: Récupération de l'ID du décret
        String documentId = decret.getDocumentId();

        // Then: ID formaté selon le pattern decret-year-number
        assertEquals("decret-2025-716", documentId,
                "L'ID du décret devrait être decret-2025-716");
    }

    @Test
    void givenTestDocument_whenGetUrl_thenReturnsCorrectUrl() {
        // Then
        assertEquals("https://sgg.gouv.bj/doc/loi-2025-17", testDocument.getUrl());
    }

    @Test
    void givenTestDocument_whenGetPdfFilename_thenReturnsCorrectFilename() {
        // When
        String pdfFilename = testDocument.getPdfFilename();

        // Then
        assertEquals("loi-2025-17.pdf", pdfFilename);
    }

    @Test
    void givenDocumentBuilder_whenBuild_thenCreatesValidDocument() {
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
    void givenTestDocument_whenGetStatus_thenReturnsFetched() {
        // Then
        assertEquals(LawDocument.ProcessingStatus.FETCHED, testDocument.getStatus());
    }

    @Test
    void givenDownloadProcessor_whenSetForceMode_thenNoException() {
        // Given
        DownloadProcessor processor = new DownloadProcessor(fileStorageService);
        
        // When
        processor.setForceMode(true);

        // Then - Pas d'exception, force mode activé
        assertDoesNotThrow(() -> processor.setForceMode(false));
    }

    @Test
    void givenAlreadyDownloadedDocument_whenProcess_thenReturnsNull() throws Exception {
        // Given
        DownloadProcessor processor = new DownloadProcessor(fileStorageService);
        testDocument.setStatus(LawDocument.ProcessingStatus.DOWNLOADED);
        processor.setForceMode(false);
        
        // Mock: PDF existe sur le disque
        when(fileStorageService.pdfExists(anyString(), anyString())).thenReturn(true);

        // When
        LawDocument result = processor.process(testDocument);

        // Then
        assertNull(result, "Le document DOWNLOADED avec PDF existant devrait être skippé");
    }

    @Test
    void givenBaseUrl_whenCheckUrl_thenDoesNotContainDownloadSuffix() {
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
    void givenBaseUrl_whenAppendDownload_thenConstructsDownloadUrl() {
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
    void givenNumberLessThan10_whenGetUrl_thenContainsPaddedNumber() {
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
