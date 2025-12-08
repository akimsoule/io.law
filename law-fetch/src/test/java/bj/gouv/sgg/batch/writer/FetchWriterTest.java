package bj.gouv.sgg.batch.writer;

import bj.gouv.sgg.model.FetchResult;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchResultRepository;
import bj.gouv.sgg.repository.LawDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour FetchWriter
 */
@ExtendWith(MockitoExtension.class)
class FetchWriterTest {

    @Mock
    private FetchResultRepository repository;

    @Mock
    private LawDocumentRepository lawDocumentRepository;

    private FetchWriter writer;

    @Captor
    private ArgumentCaptor<List<FetchResult>> resultsCaptor;
    
    @Captor
    private ArgumentCaptor<List<LawDocument>> documentsCaptor;

    @BeforeEach
    void setUp() {
        writer = new FetchWriter(repository, lawDocumentRepository);
    }

    @Test
    void givenNewDocumentWhenWriteThenSavesToRepository() throws Exception {
        // Given: 1 document nouveau (pas encore dans fetch_results)
        LawDocument doc = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .url("https://sgg.gouv.bj/doc/loi-2024-15")
            .exists(true)
            .build();

        Chunk<LawDocument> chunk = new Chunk<>(List.of(doc));
        when(repository.findByDocumentId("loi-2024-15")).thenReturn(java.util.Optional.empty());

        // When: Écriture du chunk
        writer.write(chunk);

        // Then: FetchResult sauvegardé avec les bonnes informations
        verify(repository).saveAll(resultsCaptor.capture());
        List<FetchResult> saved = resultsCaptor.getValue();
        assertEquals(1, saved.size(), "1 FetchResult devrait être sauvegardé");
        assertEquals("loi-2024-15", saved.get(0).getDocumentId(), 
                "DocumentId devrait être loi-2024-15");
        assertEquals("loi", saved.get(0).getDocumentType(),
                "Type devrait être loi");
    }

    @Test
    void givenExistingDocumentWhenWriteInNormalModeThenSkips() throws Exception {
        // Given: 1 document existant déjà dans fetch_results, mode normal (force=false)
        LawDocument doc = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .build();

        FetchResult existingResult = FetchResult.builder()
            .id(1L)
            .documentId("loi-2024-15")
            .build();

        Chunk<LawDocument> chunk = new Chunk<>(List.of(doc));
        when(repository.findByDocumentId("loi-2024-15")).thenReturn(java.util.Optional.of(existingResult));
        writer.setForceMode(false);

        // When: Écriture du chunk en mode normal
        writer.write(chunk);

        // Then: Aucune sauvegarde, aucune suppression (document skippé)
        verify(repository, never()).saveAll(anyList());
        verify(repository, never()).deleteByDocumentId(anyString());
    }

    @Test
    void givenExistingDocumentWhenWriteInForceModeThenUpdatesViaUpsert() throws Exception {
        // Given: 1 document existant dans fetch_results, mode force activé
        LawDocument doc = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .url("https://sgg.gouv.bj/doc/loi-2024-15")
            .exists(true)
            .build();

        FetchResult existingResult = FetchResult.builder()
            .id(1L)
            .documentId("loi-2024-15")
            .documentType("loi")
            .year(2024)
            .number(15)
            .build();

        Chunk<LawDocument> chunk = new Chunk<>(List.of(doc));
        when(repository.findByDocumentId("loi-2024-15")).thenReturn(java.util.Optional.of(existingResult));
        writer.setForceMode(true);

        // When: Écriture du chunk en mode force
        writer.write(chunk);

        // Then: ✅ UPSERT - Pas de delete, juste update via save()
        verify(repository, never()).deleteByDocumentId(anyString());
        verify(repository).saveAll(resultsCaptor.capture());
        
        List<FetchResult> saved = resultsCaptor.getValue();
        assertEquals(1, saved.size(), "1 FetchResult devrait être sauvegardé");
        assertEquals("loi-2024-15", saved.get(0).getDocumentId(),
                "DocumentId devrait être loi-2024-15");
        assertEquals(1L, saved.get(0).getId(), "ID devrait être conservé (UPDATE, pas INSERT)");
    }

    @Test
    void givenMultipleDocumentsWhenWriteThenSavesAll() throws Exception {
        // Given: 2 documents nouveaux (1 loi + 1 décret)
        LawDocument doc1 = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .url("https://sgg.gouv.bj/doc/loi-2024-15")
            .exists(true)
            .build();

        LawDocument doc2 = LawDocument.builder()
            .type("decret")
            .year(2025)
            .number(716)
            .url("https://sgg.gouv.bj/doc/decret-2025-716")
            .exists(true)
            .build();

        Chunk<LawDocument> chunk = new Chunk<>(List.of(doc1, doc2));
        when(repository.findByDocumentId(anyString())).thenReturn(java.util.Optional.empty());

        // When: Écriture du chunk contenant 2 documents
        writer.write(chunk);

        // Then: Les 2 FetchResults sont sauvegardés
        verify(repository).saveAll(resultsCaptor.capture());
        List<FetchResult> saved = resultsCaptor.getValue();
        assertEquals(2, saved.size(), "2 FetchResults devraient être sauvegardés");
    }

    @Test
    void givenEmptyChunkWhenWriteThenDoesNothing() throws Exception {
        // Given: Chunk vide (aucun document)
        Chunk<LawDocument> chunk = new Chunk<>(List.of());

        // When: Écriture du chunk vide
        writer.write(chunk);

        // Then: Aucune sauvegarde
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void givenWriterWhenSetForceModesThenNoException() {
        // When: Activation puis désactivation du mode force
        writer.setForceMode(true);

        // Then: Aucune exception levée
        assertDoesNotThrow(() -> writer.setForceMode(false),
                "Le changement de mode force ne devrait pas lever d'exception");
    }
}
