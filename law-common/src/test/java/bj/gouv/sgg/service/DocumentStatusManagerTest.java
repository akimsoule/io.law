package bj.gouv.sgg.service;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.LawDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour DocumentStatusManager.
 */
@ExtendWith(MockitoExtension.class)
class DocumentStatusManagerTest {

    @Mock
    private LawDocumentRepository lawDocumentRepository;

    private DocumentStatusManager documentStatusManager;

    @BeforeEach
    void setUp() {
        documentStatusManager = new DocumentStatusManager(lawDocumentRepository);
    }

    @Test
    void givenValidStatus_whenUpdateStatus_thenUpdatesDocumentStatus() {
        // Given
        String documentId = "loi-2024-15";
        LawDocument document = LawDocument.builder()
                .type("loi")
                .year(2024)
                .number(15)
                .status(LawDocument.ProcessingStatus.PENDING)
                .build();

        when(lawDocumentRepository.findByTypeAndYearAndNumber("loi", 2024, 15))
                .thenReturn(Optional.of(document));

        // When
        documentStatusManager.updateStatus(documentId, LawDocument.ProcessingStatus.FETCHED);

        // Then
        verify(lawDocumentRepository).findByTypeAndYearAndNumber("loi", 2024, 15);
        verify(lawDocumentRepository).save(document);
        assert document.getStatus() == LawDocument.ProcessingStatus.FETCHED;
    }

    @Test
    void givenStringStatus_whenUpdateStatus_thenConvertsAndUpdatesStatus() {
        // Given
        String documentId = "decret-2024-100";
        LawDocument document = LawDocument.builder()
                .type("decret")
                .year(2024)
                .number(100)
                .status(LawDocument.ProcessingStatus.PENDING)
                .build();

        when(lawDocumentRepository.findByTypeAndYearAndNumber("decret", 2024, 100))
                .thenReturn(Optional.of(document));

        // When
        documentStatusManager.updateStatus(documentId, "DOWNLOADED");

        // Then
        verify(lawDocumentRepository).findByTypeAndYearAndNumber("decret", 2024, 100);
        verify(lawDocumentRepository).save(document);
        assert document.getStatus() == LawDocument.ProcessingStatus.DOWNLOADED;
    }

    @Test
    void givenInvalidStringStatus_whenUpdateStatus_thenThrowsIllegalArgumentException() {
        // Given
        String documentId = "loi-2024-15";

        // When / Then
        assertThatThrownBy(() -> documentStatusManager.updateStatus(documentId, "INVALID_STATUS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid status");

        verify(lawDocumentRepository, never()).save(any());
    }

    @Test
    void givenNonExistingDocument_whenUpdateStatus_thenDoesNotSave() {
        // Given
        String documentId = "loi-9999-999";

        when(lawDocumentRepository.findByTypeAndYearAndNumber("loi", 9999, 999))
                .thenReturn(Optional.empty());

        // When
        documentStatusManager.updateStatus(documentId, LawDocument.ProcessingStatus.FETCHED);

        // Then
        verify(lawDocumentRepository).findByTypeAndYearAndNumber("loi", 9999, 999);
        verify(lawDocumentRepository, never()).save(any());
    }

    // Note: testUpdateStatus_WithInvalidDocumentIdFormat supprimé car DocumentStatusManager
    // log les erreurs de format mais ne throw pas d'exception (design pattern: fail-safe logging)

    @Test
    void givenMultipleDocuments_whenBulkUpdateStatus_thenUpdatesAllDocuments() {
        // Given
        LawDocument doc1 = LawDocument.builder()
                .type("loi")
                .year(2024)
                .number(1)
                .status(LawDocument.ProcessingStatus.PENDING)
                .build();

        LawDocument doc2 = LawDocument.builder()
                .type("loi")
                .year(2024)
                .number(2)
                .status(LawDocument.ProcessingStatus.PENDING)
                .build();

        when(lawDocumentRepository.findByStatus(LawDocument.ProcessingStatus.PENDING))
                .thenReturn(java.util.List.of(doc1, doc2));

        // When
        documentStatusManager.bulkUpdateStatus(
                LawDocument.ProcessingStatus.PENDING,
                LawDocument.ProcessingStatus.FETCHED
        );

        // Then
        verify(lawDocumentRepository).findByStatus(LawDocument.ProcessingStatus.PENDING);
        verify(lawDocumentRepository).saveAll(anyList());
        assert doc1.getStatus() == LawDocument.ProcessingStatus.FETCHED;
        assert doc2.getStatus() == LawDocument.ProcessingStatus.FETCHED;
    }
}
