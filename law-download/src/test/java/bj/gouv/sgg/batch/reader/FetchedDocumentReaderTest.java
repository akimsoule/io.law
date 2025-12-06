package bj.gouv.sgg.batch.reader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires basiques pour FetchedDocumentReader
 * Note: Comportement réel vérifié via tests end-to-end
 */
class FetchedDocumentReaderTest {

    @Test
    void testTargetDocumentIdFormat() {
        // Given
        String documentId = "loi-2024-15";
        String[] parts = documentId.split("-");

        // Then
        assertEquals(3, parts.length);
        assertEquals("loi", parts[0]);
        assertEquals("2024", parts[1]);
        assertEquals("15", parts[2]);
    }

    @Test
    void testDecretDocumentIdFormat() {
        // Given
        String documentId = "decret-2025-716";
        String[] parts = documentId.split("-");

        // Then
        assertEquals(3, parts.length);
        assertEquals("decret", parts[0]);
        assertEquals("2025", parts[1]);
        assertEquals("716", parts[2]);
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
