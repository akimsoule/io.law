package bj.gouv.sgg.writer;

import bj.gouv.sgg.batch.writer.FetchWriter;
import bj.gouv.sgg.model.FetchResult;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchResultRepository;
import bj.gouv.sgg.repository.LawDocumentRepository;
import bj.gouv.sgg.service.NotFoundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FetchWriterTest {

    @Mock
    private FetchResultRepository fetchResultRepository;

    @Mock
    private LawDocumentRepository lawDocumentRepository;

    @Mock
    private NotFoundService notFoundService;

    @Captor
    private ArgumentCaptor<List<FetchResult>> fetchResultCaptor;

    @Captor
    private ArgumentCaptor<List<LawDocument>> lawDocumentCaptor;

    private FetchWriter writer;

    @BeforeEach
    void setUp() {
        writer = new FetchWriter(fetchResultRepository, lawDocumentRepository, notFoundService);
        when(fetchResultRepository.findByDocumentId(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void givenNewDocuments_whenWrite_thenSaveAll() {
        // Given
        LawDocument doc = LawDocument.builder()
            .type("loi")
            .year(2025)
            .number(1)
            .url("https://sgg.gouv.bj/doc/loi-2025-1")
            .exists(true)
            .status(LawDocument.ProcessingStatus.FETCHED)
            .build();

        Chunk<LawDocument> chunk = new Chunk<>(List.of(doc));

        // When
        writer.write(chunk);

        // Then
        verify(fetchResultRepository).saveAll(fetchResultCaptor.capture());
        verify(lawDocumentRepository).saveAll(lawDocumentCaptor.capture());

        List<FetchResult> savedFetchResults = fetchResultCaptor.getValue();
        assertThat(savedFetchResults).hasSize(1);
        assertThat(savedFetchResults.get(0).getDocumentId()).isEqualTo("loi-2025-1");
        assertThat(savedFetchResults.get(0).getExists()).isTrue();

        List<LawDocument> savedDocuments = lawDocumentCaptor.getValue();
        assertThat(savedDocuments).hasSize(1);
        assertThat(savedDocuments.get(0).getDocumentId()).isEqualTo("loi-2025-1");
    }

    @Test
    void givenExistingDocumentsInForceMode_whenWrite_thenUpdateAll() {
        // Given
        writer.setForceMode(true);

        FetchResult existing = FetchResult.builder()
            .documentId("loi-2025-1")
            .status("PENDING")
            .build();

        when(fetchResultRepository.findByDocumentId("loi-2025-1"))
            .thenReturn(Optional.of(existing));

        LawDocument doc = LawDocument.builder()
            .type("loi")
            .year(2025)
            .number(1)
            .url("https://sgg.gouv.bj/doc/loi-2025-1")
            .exists(true)
            .status(LawDocument.ProcessingStatus.FETCHED)
            .build();

        Chunk<LawDocument> chunk = new Chunk<>(List.of(doc));

        // When
        writer.write(chunk);

        // Then
        verify(fetchResultRepository).saveAll(fetchResultCaptor.capture());
        
        List<FetchResult> savedResults = fetchResultCaptor.getValue();
        assertThat(savedResults).hasSize(1);
        assertThat(savedResults.get(0).getStatus()).isEqualTo("FETCHED"); // Mis à jour
    }

    @Test
    void givenExistingDocumentsInNormalMode_whenWrite_thenSkipAll() {
        // Given
        writer.setForceMode(false);

        FetchResult existing = FetchResult.builder()
            .documentId("loi-2025-1")
            .status("FETCHED")
            .build();

        when(fetchResultRepository.findByDocumentId("loi-2025-1"))
            .thenReturn(Optional.of(existing));

        LawDocument doc = LawDocument.builder()
            .type("loi")
            .year(2025)
            .number(1)
            .url("https://sgg.gouv.bj/doc/loi-2025-1")
            .exists(true)
            .status(LawDocument.ProcessingStatus.FETCHED)
            .build();

        Chunk<LawDocument> chunk = new Chunk<>(List.of(doc));

        // When
        writer.write(chunk);

        // Then
        verify(fetchResultRepository, never()).saveAll(any());
        verify(lawDocumentRepository, never()).saveAll(any());
    }

    @Test
    void write_shouldConsolidateNotFoundRangesWhenEnabled() {
        // Given
        writer.setEnableNotFoundConsolidation(true);

        LawDocument doc1 = LawDocument.builder()
            .type("loi")
            .year(2020)
            .number(100)
            .exists(false)
            .status(LawDocument.ProcessingStatus.FAILED)
            .build();

        LawDocument doc2 = LawDocument.builder()
            .type("loi")
            .year(2020)
            .number(101)
            .exists(false)
            .status(LawDocument.ProcessingStatus.FAILED)
            .build();

        Chunk<LawDocument> chunk = new Chunk<>(List.of(doc1, doc2));

        // When
        writer.write(chunk);

        // Then
        verify(notFoundService).addNotFoundDocuments(argThat(docs -> 
            docs.size() == 2 && docs.stream().allMatch(d -> !d.isExists())
        ));
    }
}
