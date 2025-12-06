package bj.gouv.sgg.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour FetchCursor
 */
class FetchCursorTest {

    @Test
    void testBuilderPattern() {
        // Given & When
        LocalDateTime now = LocalDateTime.now();
        FetchCursor cursor = FetchCursor.builder()
            .cursorType("fetch-previous")
            .documentType("loi")
            .currentYear(2024)
            .currentNumber(150)
            .updatedAt(now)
            .build();

        // Then
        assertNotNull(cursor);
        assertEquals("fetch-previous", cursor.getCursorType());
        assertEquals("loi", cursor.getDocumentType());
        assertEquals(2024, cursor.getCurrentYear());
        assertEquals(150, cursor.getCurrentNumber());
        assertEquals(now, cursor.getUpdatedAt());
    }

    @Test
    void testSetters() {
        // Given
        FetchCursor cursor = FetchCursor.builder()
            .cursorType("fetch-current")
            .documentType("loi")
            .currentYear(2024)
            .currentNumber(1)
            .updatedAt(LocalDateTime.now())
            .build();

        // When
        cursor.setCurrentNumber(200);
        cursor.setCurrentYear(2025);

        // Then
        assertEquals(200, cursor.getCurrentNumber());
        assertEquals(2025, cursor.getCurrentYear());
    }

    @Test
    void testDecretCursor() {
        // Given & When
        FetchCursor cursor = FetchCursor.builder()
            .cursorType("fetch-previous")
            .documentType("decret")
            .currentYear(2025)
            .currentNumber(716)
            .updatedAt(LocalDateTime.now())
            .build();

        // Then
        assertEquals("decret", cursor.getDocumentType());
        assertEquals(2025, cursor.getCurrentYear());
        assertEquals(716, cursor.getCurrentNumber());
    }

    @Test
    void testCursorTypes() {
        // Given & When
        FetchCursor previousCursor = FetchCursor.builder()
            .cursorType("fetch-previous")
            .documentType("loi")
            .currentYear(2024)
            .currentNumber(1)
            .updatedAt(LocalDateTime.now())
            .build();

        FetchCursor currentCursor = FetchCursor.builder()
            .cursorType("fetch-current")
            .documentType("loi")
            .currentYear(2025)
            .currentNumber(1)
            .updatedAt(LocalDateTime.now())
            .build();

        // Then
        assertEquals("fetch-previous", previousCursor.getCursorType());
        assertEquals("fetch-current", currentCursor.getCursorType());
    }
}
