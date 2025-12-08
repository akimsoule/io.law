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
    void givenNewDocumentWhenFetchSingleDocumentThenReturnsDownloadedResult() {
        // Given: Un document loi-2024-15 non existant en BD, serveur répond HTTP 200
        when(repository.findByDocumentId("loi-2024-15")).thenReturn(Optional.empty());
        when(restTemplate.headForHeaders(anyString())).thenReturn(null);

        // When: Fetch du document
        FetchResult result = service.fetchSingleDocument("loi", 2024, 15);

        // Then: Résultat créé avec status DOWNLOADED et métadonnées complètes
        assertNotNull(result, "Le résultat ne devrait pas être null");
        assertEquals("loi-2024-15", result.getDocumentId(), "L'ID document devrait être loi-2024-15");
        assertEquals("DOWNLOADED", result.getStatus(), "Le status devrait être DOWNLOADED");
        assertEquals("loi", result.getDocumentType(), "Le type devrait être loi");
        assertEquals(2024, result.getYear(), "L'année devrait être 2024");
        assertEquals(15, result.getNumber(), "Le numéro devrait être 15");
        assertNotNull(result.getFetchedAt(), "La date de fetch devrait être définie");

        verify(repository).save(resultCaptor.capture());
        FetchResult saved = resultCaptor.getValue();
        assertEquals("loi-2024-15", saved.getDocumentId(), "Le document sauvegardé devrait avoir l'ID loi-2024-15");
        assertEquals("DOWNLOADED", saved.getStatus(), "Le status sauvegardé devrait être DOWNLOADED");
    }

    @Test
    void givenNonExistentDocumentWhenFetchSingleDocumentThenReturnsNotFoundResult() {
        // Given: Un document loi-1960-999 inexistant, serveur répond HTTP 404
        when(repository.findByDocumentId("loi-1960-999")).thenReturn(Optional.empty());
        when(restTemplate.headForHeaders(anyString()))
            .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        // When: Tentative de fetch du document
        FetchResult result = service.fetchSingleDocument("loi", 1960, 999);

        // Then: Résultat avec status NOT_FOUND et message d'erreur 404
        assertNotNull(result, "Le résultat ne devrait pas être null même pour document introuvable");
        assertEquals("loi-1960-999", result.getDocumentId(), "L'ID document devrait être loi-1960-999");
        assertEquals("NOT_FOUND", result.getStatus(), "Le status devrait être NOT_FOUND");
        assertEquals("404 Not Found", result.getErrorMessage(), "Le message d'erreur devrait être 404 Not Found");

        verify(repository).save(resultCaptor.capture());
        FetchResult saved = resultCaptor.getValue();
        assertEquals("NOT_FOUND", saved.getStatus(), "Le status sauvegardé devrait être NOT_FOUND");
    }

    @Test
    void givenExistingPendingDocumentWhenFetchSingleDocumentThenUpdatesStatusToDownloaded() {
        // Given: Un document loi-2024-15 existant avec status PENDING en BD
        FetchResult existing = FetchResult.builder()
            .documentId("loi-2024-15")
            .documentType("loi")
            .year(2024)
            .number(15)
            .status("PENDING")
            .build();

        when(repository.findByDocumentId("loi-2024-15")).thenReturn(Optional.of(existing));
        when(restTemplate.headForHeaders(anyString())).thenReturn(null);

        // When: Re-fetch du document existant
        FetchResult result = service.fetchSingleDocument("loi", 2024, 15);

        // Then: Status mis à jour à DOWNLOADED, errorMessage effacé
        assertEquals("DOWNLOADED", result.getStatus(), "Le status devrait être mis à jour à DOWNLOADED");
        assertNull(result.getErrorMessage(), "Le message d'erreur devrait être null après succès");
        verify(repository).save(existing);
    }

    @Test
    void givenDecretParametersWhenFetchSingleDocumentThenBuildsCorrectUrl() {
        // Given: Paramètres d'un décret (type decret, année 2025, numéro 716)
        when(repository.findByDocumentId("decret-2025-716")).thenReturn(Optional.empty());
        when(restTemplate.headForHeaders(anyString())).thenReturn(null);

        // When: Fetch du décret
        FetchResult result = service.fetchSingleDocument("decret", 2025, 716);

        // Then: URL construite au format base/type-year-number
        assertEquals("https://sgg.gouv.bj/doc/decret-2025-716", result.getUrl(),
                    "L'URL devrait suivre le format base/type-year-number");
    }
}
