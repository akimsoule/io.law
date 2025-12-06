package bj.gouv.sgg.batch.writer;

import bj.gouv.sgg.model.FetchResult;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchResultRepository;
import jakarta.persistence.EntityManager;
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
    private EntityManager entityManager;

    private FetchWriter writer;

    @Captor
    private ArgumentCaptor<List<FetchResult>> resultsCaptor;

    @BeforeEach
    void setUp() {
        writer = new FetchWriter(repository, entityManager);
    }

    @Test
    void testWriteNewDocuments() throws Exception {
        // Given
        LawDocument doc = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .url("https://sgg.gouv.bj/doc/loi-2024-15")
            .exists(true)
            .build();

        Chunk<LawDocument> chunk = new Chunk<>(List.of(doc));
        when(repository.existsByDocumentId("loi-2024-15")).thenReturn(false);

        // When
        writer.write(chunk);

        // Then
        verify(repository).saveAll(resultsCaptor.capture());
        List<FetchResult> saved = resultsCaptor.getValue();
        assertEquals(1, saved.size());
        assertEquals("loi-2024-15", saved.get(0).getDocumentId());
        assertEquals("loi", saved.get(0).getDocumentType());
    }

    @Test
    void testWriteSkipsExistingInNormalMode() throws Exception {
        // Given
        LawDocument doc = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .build();

        Chunk<LawDocument> chunk = new Chunk<>(List.of(doc));
        when(repository.existsByDocumentId("loi-2024-15")).thenReturn(true);
        writer.setForceMode(false);

        // When
        writer.write(chunk);

        // Then
        verify(repository, never()).saveAll(anyList());
        verify(repository, never()).deleteByDocumentId(anyString());
    }

    @Test
    void testWriteDeletesAndRecreatesInForceMode() throws Exception {
        // Given
        LawDocument doc = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .url("https://sgg.gouv.bj/doc/loi-2024-15")
            .exists(true)
            .build();

        Chunk<LawDocument> chunk = new Chunk<>(List.of(doc));
        when(repository.existsByDocumentId("loi-2024-15")).thenReturn(true);
        writer.setForceMode(true);

        // When
        writer.write(chunk);

        // Then
        verify(repository).deleteByDocumentId("loi-2024-15");
        verify(entityManager).flush();
        verify(repository).saveAll(resultsCaptor.capture());
        
        List<FetchResult> saved = resultsCaptor.getValue();
        assertEquals(1, saved.size());
        assertEquals("loi-2024-15", saved.get(0).getDocumentId());
    }

    @Test
    void testWriteMultipleDocuments() throws Exception {
        // Given
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
        when(repository.existsByDocumentId(anyString())).thenReturn(false);

        // When
        writer.write(chunk);

        // Then
        verify(repository).saveAll(resultsCaptor.capture());
        List<FetchResult> saved = resultsCaptor.getValue();
        assertEquals(2, saved.size());
    }

    @Test
    void testWriteEmptyChunk() throws Exception {
        // Given
        Chunk<LawDocument> chunk = new Chunk<>(List.of());

        // When
        writer.write(chunk);

        // Then
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void testSetForceMode() {
        // When
        writer.setForceMode(true);

        // Then - Ne devrait pas lever d'exception
        assertDoesNotThrow(() -> writer.setForceMode(false));
    }
}
