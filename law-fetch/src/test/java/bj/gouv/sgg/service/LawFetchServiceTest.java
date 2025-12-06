package bj.gouv.sgg.service;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.FetchResult;
import bj.gouv.sgg.repository.FetchResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour LawFetchService
 */
@ExtendWith(MockitoExtension.class)
class LawFetchServiceTest {

    @Mock
    private FetchResultRepository repository;

    @Mock
    private LawProperties properties;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private LawFetchService service;

    @Captor
    private ArgumentCaptor<FetchResult> resultCaptor;

    @BeforeEach
    void setUp() {
        when(properties.getBaseUrl()).thenReturn("https://sgg.gouv.bj/doc");
    }

    @Test
    void testFetchSingleDocumentSuccess() {
        // Given
        when(repository.findByDocumentId("loi-2024-15")).thenReturn(Optional.empty());
        when(restTemplate.headForHeaders(anyString())).thenReturn(null);

        // When
        FetchResult result = service.fetchSingleDocument("loi", 2024, 15);

        // Then
        assertNotNull(result);
        assertEquals("loi-2024-15", result.getDocumentId());
        assertEquals("DOWNLOADED", result.getStatus());
        assertEquals("loi", result.getDocumentType());
        assertEquals(2024, result.getYear());
        assertEquals(15, result.getNumber());
        assertNotNull(result.getFetchedAt());

        verify(repository).save(resultCaptor.capture());
        FetchResult saved = resultCaptor.getValue();
        assertEquals("loi-2024-15", saved.getDocumentId());
        assertEquals("DOWNLOADED", saved.getStatus());
    }

    @Test
    void testFetchSingleDocumentNotFound() {
        // Given
        when(repository.findByDocumentId("loi-1960-999")).thenReturn(Optional.empty());
        when(restTemplate.headForHeaders(anyString()))
            .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        // When
        FetchResult result = service.fetchSingleDocument("loi", 1960, 999);

        // Then
        assertNotNull(result);
        assertEquals("loi-1960-999", result.getDocumentId());
        assertEquals("NOT_FOUND", result.getStatus());
        assertEquals("404 Not Found", result.getErrorMessage());

        verify(repository).save(resultCaptor.capture());
        FetchResult saved = resultCaptor.getValue();
        assertEquals("NOT_FOUND", saved.getStatus());
    }

    @Test
    void testFetchSingleDocumentUpdateExisting() {
        // Given
        FetchResult existing = FetchResult.builder()
            .documentId("loi-2024-15")
            .documentType("loi")
            .year(2024)
            .number(15)
            .status("PENDING")
            .build();

        when(repository.findByDocumentId("loi-2024-15")).thenReturn(Optional.of(existing));
        when(restTemplate.headForHeaders(anyString())).thenReturn(null);

        // When
        FetchResult result = service.fetchSingleDocument("loi", 2024, 15);

        // Then
        assertEquals("DOWNLOADED", result.getStatus());
        assertNull(result.getErrorMessage());
        verify(repository).save(existing);
    }

    @Test
    void testBuildCorrectUrl() {
        // Given
        when(repository.findByDocumentId("decret-2025-716")).thenReturn(Optional.empty());
        when(restTemplate.headForHeaders(anyString())).thenReturn(null);

        // When
        FetchResult result = service.fetchSingleDocument("decret", 2025, 716);

        // Then
        assertEquals("https://sgg.gouv.bj/doc/decret-2025-716", result.getUrl());
    }
}
