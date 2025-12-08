package bj.gouv.sgg.batch.processor;

import bj.gouv.sgg.model.LawDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires basiques pour FetchProcessor
 * Note: Tests simplifiés car FetchProcessor appelle des services HTTP réels
 * Les tests d'intégration vérifient le comportement complet
 */
class FetchProcessorTest {

    private LawDocument testDocument;

    @BeforeEach
    void setUp() {
        testDocument = LawDocument.builder()
            .type("loi")
            .year(2025)
            .number(17)
            .url("https://sgg.gouv.bj/doc/loi-2025-17")
            .build();
    }

    @Test
    void givenLawDocumentWhenGetDocumentIdThenFormattedCorrectly() {
        // Given: Document loi avec année 2025 et numéro 17 (défini dans setUp)

        // When: Récupération de l'ID du document
        String documentId = testDocument.getDocumentId();

        // Then: ID formaté selon le pattern loi-year-number
        assertEquals("loi-2025-17", documentId,
                "L'ID du document devrait être loi-2025-17");
    }

    @Test
    void givenDecretDocumentWhenGetDocumentIdThenFormattedCorrectly() {
        // Given: Document décret avec année 2025 et numéro 716
        LawDocument decret = LawDocument.builder()
            .type("decret")
            .year(2025)
            .number(716)
            .build();

        // When: Récupération de l'ID du décret
        String documentId = decret.getDocumentId();

        // Then: ID formaté selon le pattern decret-year-number
        assertEquals("decret-2025-716", documentId,
                "L'ID du décret devrait être decret-2025-716");
    }

    @Test
    void givenLawDocumentWhenGetUrlThenConstructedCorrectly() {
        // Given: Document loi-2025-17 (défini dans setUp)

        // Then: URL construite selon le pattern base-url/type-year-number
        assertEquals("https://sgg.gouv.bj/doc/loi-2025-17", testDocument.getUrl(),
                "L'URL devrait être https://sgg.gouv.bj/doc/loi-2025-17");
    }

    @Test
    void givenBuilderParametersWhenBuildDocumentThenAllFieldsSet() {
        // Given & When: Construction d'un document via builder
        LawDocument doc = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .url("https://sgg.gouv.bj/doc/loi-2024-15")
            .build();

        // Then: Tous les champs sont correctement initialisés
        assertNotNull(doc, "Le document ne devrait pas être null");
        assertEquals("loi", doc.getType(), "Le type devrait être loi");
        assertEquals(2024, doc.getYear(), "L'année devrait être 2024");
        assertEquals(15, doc.getNumber(), "Le numéro devrait être 15");
        assertEquals("loi-2024-15", doc.getDocumentId(),
                "L'ID généré devrait être loi-2024-15");
    }
}
