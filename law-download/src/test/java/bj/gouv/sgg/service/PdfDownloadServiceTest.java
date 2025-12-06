package bj.gouv.sgg.service;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.LawDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour PdfDownloadService
 */
@ExtendWith(MockitoExtension.class)
class PdfDownloadServiceTest {

    @Mock
    private LawDocumentRepository repository;

    @Mock
    private LawProperties properties;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "https://sgg.gouv.bj/doc";
    }

    @Test
    void testBuildPdfUrl() {
        // Given
        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .build();

        // When
        String url = baseUrl + "/" + document.getDocumentId();

        // Then
        assertEquals("https://sgg.gouv.bj/doc/loi-2024-15", url);
    }

    @Test
    void testBuildDecretPdfUrl() {
        // Given
        LawDocument document = LawDocument.builder()
            .type("decret")
            .year(2025)
            .number(716)
            .build();

        // When
        String url = baseUrl + "/" + document.getDocumentId();

        // Then
        assertEquals("https://sgg.gouv.bj/doc/decret-2025-716", url);
    }

    @Test
    void testDocumentWithUrl() {
        // Given
        String expectedUrl = "https://sgg.gouv.bj/doc/loi-2024-15";
        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .url(expectedUrl)
            .build();

        // Then
        assertEquals(expectedUrl, document.getUrl());
    }

    @Test
    void testPdfFilenameGeneration() {
        // Given
        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .build();

        // When
        String pdfFilename = document.getPdfFilename();

        // Then
        assertEquals("loi-2024-15.pdf", pdfFilename);
    }
}
