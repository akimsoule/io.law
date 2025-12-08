package bj.gouv.sgg.service;

import bj.gouv.sgg.model.FetchNotFoundRange;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchNotFoundRangeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour NotFoundRangeService
 */
@ExtendWith(MockitoExtension.class)
class NotFoundRangeServiceTest {

    @Mock
    private FetchNotFoundRangeRepository repository;

    @InjectMocks
    private NotFoundRangeService service;

    @Captor
    private ArgumentCaptor<FetchNotFoundRange> rangeCaptor;

    @Test
    void givenNewNotFoundDocumentWhenAddNotFoundDocumentThenCreatesNewRange() {
        // Given
        LawDocument doc = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(999)
            .exists(false)
            .build();

        when(repository.findOverlappingRanges("loi", 2024, 999))
            .thenReturn(Collections.emptyList());

        // When
        service.addNotFoundDocument(doc);

        // Then
        verify(repository).save(rangeCaptor.capture());
        FetchNotFoundRange saved = rangeCaptor.getValue();
        assertEquals("loi", saved.getDocumentType());
        assertEquals(2024, saved.getYear());
        assertEquals(999, saved.getNumberMin());
        assertEquals(999, saved.getNumberMax());
        assertEquals(1, saved.getDocumentCount());
    }

    @Test
    void givenAdjacentRangeWhenAddNotFoundDocumentThenMergesRanges() {
        // Given
        LawDocument doc = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(100)
            .exists(false)
            .build();

        FetchNotFoundRange existingRange = FetchNotFoundRange.builder()
            .documentType("loi")
            .year(2024)
            .numberMin(98)
            .numberMax(99)
            .documentCount(2)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(repository.findOverlappingRanges("loi", 2024, 100))
            .thenReturn(List.of(existingRange));

        // When
        service.addNotFoundDocument(doc);

        // Then
        verify(repository).save(rangeCaptor.capture());
        FetchNotFoundRange merged = rangeCaptor.getValue();
        assertEquals(98, merged.getNumberMin());
        assertEquals(100, merged.getNumberMax());
        assertEquals(3, merged.getDocumentCount());
    }

    @Test
    void givenDocumentNumbersWhenIsInNotFoundRangeThenReturnsCorrectStatus() {
        // Given
        when(repository.isInNotFoundRange("loi", 2024, 999)).thenReturn(true);
        when(repository.isInNotFoundRange("loi", 2024, 1)).thenReturn(false);

        // When & Then
        assertTrue(service.isInNotFoundRange("loi", 2024, 999));
        assertFalse(service.isInNotFoundRange("loi", 2024, 1));
    }

    @Test
    void givenMixedDocumentsWhenAddNotFoundDocumentsThenFiltersExistingOnes() {
        // Given
        LawDocument existing = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(1)
            .exists(true)
            .build();

        LawDocument notFound = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(999)
            .exists(false)
            .build();

        List<LawDocument> docs = List.of(existing, notFound);
        when(repository.findOverlappingRanges(anyString(), anyInt(), anyInt()))
            .thenReturn(Collections.emptyList());

        // When
        service.addNotFoundDocuments(docs);

        // Then - Seulement le notFound devrait être traité
        verify(repository, times(1)).save(any(FetchNotFoundRange.class));
    }

    @Test
    void givenEmptyListWhenAddNotFoundDocumentsThenPerformsNoSave() {
        // When
        service.addNotFoundDocuments(Collections.emptyList());

        // Then - Aucune sauvegarde
        verify(repository, never()).save(any());
    }
}
