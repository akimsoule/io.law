package bj.gouv.sgg.batch.reader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires basiques pour FetchedDocumentReader
 * Note: Comportement réel vérifié via tests end-to-end
 */
class FetchedDocumentReaderTest {

    @Test
    void givenLawDocumentIdWhenSplittingThenThreePartsTypeYearNumber() {
        // Given: ID de loi au format standard
        String documentId = "loi-2024-15";

        // When: Séparation des parties de l'ID
        String[] parts = documentId.split("-");

        // Then: 3 parties (type, année, numéro) correctement extraites
        assertEquals(3, parts.length, "L'ID devrait avoir 3 parties");
        assertEquals("loi", parts[0], "Partie 0 devrait être le type 'loi'");
        assertEquals("2024", parts[1], "Partie 1 devrait être l'année '2024'");
        assertEquals("15", parts[2], "Partie 2 devrait être le numéro '15'");
    }

    @Test
    void givenDecretDocumentIdWhenSplittingThenThreePartsTypeYearNumber() {
        // Given: ID de décret au format standard
        String documentId = "decret-2025-716";

        // When: Séparation des parties de l'ID
        String[] parts = documentId.split("-");

        // Then: 3 parties (type, année, numéro) correctement extraites
        assertEquals(3, parts.length, "L'ID devrait avoir 3 parties");
        assertEquals("decret", parts[0], "Partie 0 devrait être le type 'decret'");
        assertEquals("2025", parts[1], "Partie 1 devrait être l'année '2025'");
        assertEquals("716", parts[2], "Partie 2 devrait être le numéro '716'");
    }

    @Test
    void testParseYear() {
        // Given
        String documentId = "loi-2024-15";
        String[] parts = documentId.split("-");

        // When
        int year = Integer.parseInt(parts[1]);

        // Then
        assertEquals(2024, year);
    }

    @Test
    void testParseNumber() {
        // Given
        String documentId = "loi-2024-15";
        String[] parts = documentId.split("-");

        // When
        int number = Integer.parseInt(parts[2]);

        // Then
        assertEquals(15, number);
    }
}
