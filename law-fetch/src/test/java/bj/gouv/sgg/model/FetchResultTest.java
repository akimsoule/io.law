package bj.gouv.sgg.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour FetchResult
 */
class FetchResultTest {

    @Test
    void givenDocumentDataWhenBuildFetchResultThenCreatesValidInstance() {
        // Given & When
        LocalDateTime now = LocalDateTime.now();
        FetchResult result = FetchResult.builder()
            .documentId("loi-2024-15")
            .documentType("loi")
            .year(2024)
            .number(15)
            .url("https://sgg.gouv.bj/doc/loi-2024-15")
            .status("DOWNLOADED")
            .exists(true)
            .fetchedAt(now)
            .build();

        // Then
        assertNotNull(result);
        assertEquals("loi-2024-15", result.getDocumentId());
        assertEquals("loi", result.getDocumentType());
        assertEquals(2024, result.getYear());
        assertEquals(15, result.getNumber());
        assertEquals("https://sgg.gouv.bj/doc/loi-2024-15", result.getUrl());
        assertEquals("DOWNLOADED", result.getStatus());
        assertTrue(result.getExists());
        assertEquals(now, result.getFetchedAt());
    }

    @Test
    void givenExistingResultWhenSetStatusAndExistsThenUpdatesValues() {
        // Given
        FetchResult result = FetchResult.builder()
            .documentId("loi-2024-15")
            .status("PENDING")
            .build();

        // When
        result.setStatus("DOWNLOADED");
        result.setExists(true);
        result.setErrorMessage(null);

        // Then
        assertEquals("DOWNLOADED", result.getStatus());
        assertTrue(result.getExists());
        assertNull(result.getErrorMessage());
    }

    @Test
    void givenNotFoundDocumentWhenBuildResultThenSetsNotFoundStatus() {
        // Given & When
        FetchResult result = FetchResult.builder()
            .documentId("loi-1960-999")
            .status("NOT_FOUND")
            .exists(false)
            .errorMessage("404 Not Found")
            .build();

        // Then
        assertEquals("NOT_FOUND", result.getStatus());
        assertFalse(result.getExists());
        assertEquals("404 Not Found", result.getErrorMessage());
    }

    @Test
    void givenDecretParametersWhenBuildResultThenCreatesDecretResult() {
        // Given & When
        FetchResult result = FetchResult.builder()
            .documentId("decret-2025-716")
            .documentType("decret")
            .year(2025)
            .number(716)
            .build();

        // Then
        assertEquals("decret-2025-716", result.getDocumentId());
        assertEquals("decret", result.getDocumentType());
    }
}
