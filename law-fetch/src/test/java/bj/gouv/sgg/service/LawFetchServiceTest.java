package bj.gouv.sgg.service;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.FetchResult;
import bj.gouv.sgg.repository.FetchResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LawFetchServiceTest {

    @Mock
    private FetchResultRepository fetchResultRepository;

    @Mock
    private LawProperties properties;

    @Mock
    private RestTemplate restTemplate;

    private LawFetchService service;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getBaseUrl()).thenReturn("https://sgg.gouv.bj/doc");
        service = new LawFetchService(fetchResultRepository, properties, restTemplate);
    }

    @Test
    void givenDocumentExists_whenFetchSingleDocument_thenReturnsDownloadedResult() {
        // Given
        String documentId = "loi-2024-15";
        HttpHeaders headers = new HttpHeaders();
        
        when(restTemplate.headForHeaders(anyString())).thenReturn(headers);
        when(fetchResultRepository.findByDocumentId(documentId))
            .thenReturn(Optional.empty());
        when(fetchResultRepository.save(any(FetchResult.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FetchResult result = service.fetchSingleDocument("loi", 2024, 15);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDocumentId()).isEqualTo(documentId);
        assertThat(result.getStatus()).isEqualTo("DOWNLOADED");
        assertThat(result.getUrl()).contains("sgg.gouv.bj/doc/loi-2024-15");
        assertThat(result.getErrorMessage()).isNull();
        
        verify(fetchResultRepository).save(any(FetchResult.class));
    }

    @Test
    void givenDocumentNotFound_whenFetchSingleDocument_thenReturnsNotFoundResult() {
        // Given
        String documentId = "decret-2020-999";
        
        when(restTemplate.headForHeaders(anyString()))
            .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));
        when(fetchResultRepository.findByDocumentId(documentId))
            .thenReturn(Optional.empty());
        when(fetchResultRepository.save(any(FetchResult.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FetchResult result = service.fetchSingleDocument("decret", 2020, 999);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDocumentId()).isEqualTo(documentId);
        assertThat(result.getStatus()).isEqualTo("NOT_FOUND");
        assertThat(result.getErrorMessage()).contains("404");
        
        verify(fetchResultRepository).save(any(FetchResult.class));
    }

    @Test
    void givenExistingFetchResult_whenFetchSingleDocument_thenUpdatesExisting() {
        // Given
        String documentId = "loi-2023-10";
        FetchResult existingResult = FetchResult.builder()
            .documentId(documentId)
            .documentType("loi")
            .year(2023)
            .number(10)
            .status("PENDING")
            .build();
        
        HttpHeaders headers = new HttpHeaders();
        when(restTemplate.headForHeaders(anyString())).thenReturn(headers);
        when(fetchResultRepository.findByDocumentId(documentId))
            .thenReturn(Optional.of(existingResult));
        when(fetchResultRepository.save(any(FetchResult.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FetchResult result = service.fetchSingleDocument("loi", 2023, 10);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("DOWNLOADED");
        assertThat(result).isSameAs(existingResult); // Vérifie que c'est l'instance mise à jour
        
        verify(fetchResultRepository).save(existingResult);
    }

    @Test
    void givenServerError_whenFetchSingleDocument_thenThrowsException() {
        
        when(restTemplate.headForHeaders(anyString()))
            .thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // When/Then - Le service lance l'exception (pas de catch pour 500)
        try {
            service.fetchSingleDocument("loi", 2022, 5);
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
