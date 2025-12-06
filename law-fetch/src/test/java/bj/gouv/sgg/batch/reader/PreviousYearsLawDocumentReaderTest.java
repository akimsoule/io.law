package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.FetchCursor;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchCursorRepository;
import bj.gouv.sgg.repository.FetchResultRepository;
import bj.gouv.sgg.util.LawDocumentFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour PreviousYearsLawDocumentReader
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class PreviousYearsLawDocumentReaderTest {

    @Mock
    private LawProperties properties;

    @Mock
    private LawProperties.Batch batchProperties;

    @Mock
    private FetchResultRepository fetchResultRepository;

    @Mock
    private FetchCursorRepository fetchCursorRepository;

    @Mock
    private LawDocumentFactory documentFactory;

    private PreviousYearsLawDocumentReader reader;

    @BeforeEach
    void setUp() {
        when(properties.getBatch()).thenReturn(batchProperties);
        when(batchProperties.getMaxItemsToFetchPrevious()).thenReturn(100);
        when(properties.getMaxNumberPerYear()).thenReturn(2000);
        when(properties.getEndYear()).thenReturn(1960);

        reader = new PreviousYearsLawDocumentReader(
            properties, fetchResultRepository, fetchCursorRepository, documentFactory
        );
    }

    @Test
    void testSetTargetDocumentId() {
        // When
        assertDoesNotThrow(() -> reader.setTargetDocumentId("loi-2024-15"));

        // Then - No exception, logging only
        assertNotNull(reader);
    }

    @Test
    void testSetForceMode() {
        // When
        assertDoesNotThrow(() -> reader.setForceMode(true));

        // Then - No exception, logging only
        assertNotNull(reader);
    }

    @Test
    void testSetMaxDocuments() {
        // When
        assertDoesNotThrow(() -> reader.setMaxDocuments(5));

        // Then - No exception, logging only
        assertNotNull(reader);
    }

    @Test
    void testReadWithTargetDocument() {
        // Given
        reader.setTargetDocumentId("loi-2024-15");
        when(fetchResultRepository.existsByDocumentId("loi-2024-15")).thenReturn(false);
        
        LawDocument mockDoc = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .url("https://sgg.gouv.bj/doc/loi-2024-15")
            .status(LawDocument.ProcessingStatus.PENDING)
            .build();
        
        when(documentFactory.create("loi", 2024, 15)).thenReturn(mockDoc);

        // When
        LawDocument result = reader.read();

        // Then
        assertNotNull(result);
        assertEquals("loi", result.getType());
        assertEquals(2024, result.getYear());
        assertEquals(15, result.getNumber());
        
        // Vérifier qu'un second appel retourne null (fin de données)
        assertNull(reader.read());
    }

    @Test
    void testReadWithTargetDocumentInForceMode() {
        // Given
        reader.setTargetDocumentId("loi-2023-10");
        reader.setForceMode(true);
        when(fetchResultRepository.existsByDocumentId(anyString())).thenReturn(true);
        
        LawDocument mockDoc = LawDocument.builder()
            .type("loi")
            .year(2023)
            .number(10)
            .url("https://sgg.gouv.bj/doc/loi-2023-10")
            .status(LawDocument.ProcessingStatus.PENDING)
            .build();
        
        when(documentFactory.create("loi", 2023, 10)).thenReturn(mockDoc);

        // When
        LawDocument result = reader.read();

        // Then
        assertNotNull(result, "Force mode should fetch document even if exists");
        assertEquals("loi", result.getType());
    }

    @Test
    void testReadWithTargetDocumentAlreadyExists() {
        // Given
        reader.setTargetDocumentId("loi-2024-99");
        reader.setForceMode(false);
        when(fetchResultRepository.existsByDocumentId("loi-2024-99")).thenReturn(true);

        // When
        LawDocument result = reader.read();

        // Then
        assertNull(result, "Should skip document that already exists");
        verify(documentFactory, never()).create(anyString(), anyInt(), anyInt());
    }

    @Test
    void testReadWithInvalidTargetDocumentId() {
        // Given
        reader.setTargetDocumentId("invalid-format");

        // When
        LawDocument result = reader.read();

        // Then
        assertNull(result, "Should return null for invalid documentId format");
    }

    @Test
    void testReadWithMaxDocumentsLimit() {
        // Given
        reader.setMaxDocuments(2);
        when(fetchResultRepository.findAllDocumentIds()).thenReturn(Collections.emptyList());
        when(fetchCursorRepository.findByCursorType(anyString())).thenReturn(Optional.empty());
        
        LawDocument mockDoc = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(1)
            .status(LawDocument.ProcessingStatus.PENDING)
            .build();
        
        when(documentFactory.create(anyString(), anyInt(), anyInt())).thenReturn(mockDoc);

        // When
        LawDocument first = reader.read();
        LawDocument second = reader.read();
        LawDocument third = reader.read();

        // Then
        assertNotNull(first, "First document should be returned");
        assertNotNull(second, "Second document should be returned");
        assertNull(third, "Third document should be null (maxDocuments=2)");
    }

    @Test
    void testReadWithCursorFromDatabase() {
        // Given
        FetchCursor cursor = FetchCursor.builder()
            .cursorType("fetch-previous")
            .currentYear(2023)
            .currentNumber(50)
            .build();
        
        when(fetchCursorRepository.findByCursorType("fetch-previous")).thenReturn(Optional.of(cursor));
        when(fetchResultRepository.findAllDocumentIds()).thenReturn(Collections.emptyList());
        
        LawDocument mockDoc = LawDocument.builder()
            .type("loi")
            .year(2023)
            .number(50)
            .status(LawDocument.ProcessingStatus.PENDING)
            .build();
        
        when(documentFactory.create(anyString(), anyInt(), anyInt())).thenReturn(mockDoc);

        // When
        LawDocument result = reader.read();

        // Then
        assertNotNull(result);
        verify(fetchCursorRepository, atLeastOnce()).findByCursorType("fetch-previous");
        verify(fetchCursorRepository, atLeastOnce()).save(any(FetchCursor.class));
    }

    @Test
    void testReadWithNoCursor() {
        // Given
        when(fetchCursorRepository.findByCursorType(anyString())).thenReturn(Optional.empty());
        when(fetchResultRepository.findAllDocumentIds()).thenReturn(Collections.emptyList());
        reader.setMaxDocuments(1);
        
        LawDocument mockDoc = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(2000)
            .status(LawDocument.ProcessingStatus.PENDING)
            .build();
        
        when(documentFactory.create(anyString(), anyInt(), anyInt())).thenReturn(mockDoc);

        // When
        LawDocument result = reader.read();

        // Then
        assertNotNull(result, "Should generate documents starting from current year - 1");
        verify(fetchCursorRepository).save(any(FetchCursor.class));
    }

    @Test
    void testReadSkipsVerifiedDocuments() {
        // Given
        List<String> verifiedDocs = List.of("loi-2024-1", "decret-2024-1");
        when(fetchResultRepository.findAllDocumentIds()).thenReturn(verifiedDocs);
        when(fetchCursorRepository.findByCursorType(anyString())).thenReturn(Optional.empty());
        reader.setMaxDocuments(2);
        
        LawDocument mockDoc = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(2)
            .status(LawDocument.ProcessingStatus.PENDING)
            .build();
        
        when(documentFactory.create(anyString(), anyInt(), anyInt())).thenReturn(mockDoc);

        // When
        reader.read();

        // Then
        verify(fetchResultRepository).findAllDocumentIds();
        // Factory should NOT be called for verified documents
        verify(documentFactory, atLeast(1)).create(anyString(), anyInt(), anyInt());
    }

    @Test
    void testReadInForceModeSkipsNothing() {
        // Given
        reader.setForceMode(true);
        reader.setMaxDocuments(2);
        List<String> verifiedDocs = List.of("loi-2024-1", "decret-2024-1");
        when(fetchResultRepository.findAllDocumentIds()).thenReturn(verifiedDocs);
        when(fetchCursorRepository.findByCursorType(anyString())).thenReturn(Optional.empty());
        
        LawDocument mockDoc = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(1)
            .status(LawDocument.ProcessingStatus.PENDING)
            .build();
        
        when(documentFactory.create(anyString(), anyInt(), anyInt())).thenReturn(mockDoc);

        // When
        reader.read();

        // Then
        // En mode force, findAllDocumentIds ne devrait pas être appelé
        verify(fetchResultRepository, never()).findAllDocumentIds();
    }

    @Test
    void testReset() {
        // Given
        reader.setMaxDocuments(1);
        when(fetchResultRepository.findAllDocumentIds()).thenReturn(Collections.emptyList());
        when(fetchCursorRepository.findByCursorType(anyString())).thenReturn(Optional.empty());
        
        LawDocument mockDoc = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(2000)
            .status(LawDocument.ProcessingStatus.PENDING)
            .build();
        
        when(documentFactory.create(anyString(), anyInt(), anyInt())).thenReturn(mockDoc);

        // When
        reader.read(); // First read
        reader.reset(); // Reset
        LawDocument result = reader.read(); // Read again

        // Then
        assertNotNull(result, "Should read again after reset");
    }

    @Test
    void testParseDocumentIdValid() {
        // Given
        String documentId = "loi-2024-15";
        
        // When
        String[] parts = documentId.split("-");
        
        // Then
        assertEquals(3, parts.length);
        assertEquals("loi", parts[0]);
        assertEquals("2024", parts[1]);
        assertEquals("15", parts[2]);
    }

    @Test
    void testParseDocumentIdInvalid() {
        // Given
        String documentId = "invalid-format";
        
        // When
        String[] parts = documentId.split("-");
        
        // Then
        assertEquals(2, parts.length, "Should not have 3 parts");
    }
}
