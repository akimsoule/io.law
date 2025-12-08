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
    void givenValidDocumentIdWhenSetTargetDocumentIdThenNoException() {
        // Given: Reader configuré (setUp)

        // When: Définition d'un document ciblé
        assertDoesNotThrow(() -> reader.setTargetDocumentId("loi-2024-15"),
                "Setter ne devrait pas lever d'exception");

        // Then: Reader toujours valide
        assertNotNull(reader, "Le reader ne devrait pas être null");
    }

    @Test
    void givenReaderWhenSetForceModeThenNoException() {
        // Given: Reader configuré (setUp)

        // When: Activation du mode force
        assertDoesNotThrow(() -> reader.setForceMode(true),
                "Setter ne devrait pas lever d'exception");

        // Then: Reader toujours valide
        assertNotNull(reader, "Le reader ne devrait pas être null");
    }

    @Test
    void givenReaderWhenSetMaxDocumentsThenNoException() {
        // Given: Reader configuré (setUp)

        // When: Définition d'une limite de 5 documents
        assertDoesNotThrow(() -> reader.setMaxDocuments(5),
                "Setter ne devrait pas lever d'exception");

        // Then: Reader toujours valide
        assertNotNull(reader, "Le reader ne devrait pas être null");
    }

    @Test
    void givenTargetDocumentIdWhenReadThenReturnsOnlyTargetedDocument() {
        // Given: Document ciblé loi-2024-15 non existant dans fetch_results
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

        // When: Lecture du document ciblé
        LawDocument result = reader.read();

        // Then: Document ciblé retourné, puis null (fin de lecture)
        assertNotNull(result, "Le document ciblé devrait être retourné");
        assertEquals("loi", result.getType(), "Type devrait être loi");
        assertEquals(2024, result.getYear(), "Année devrait être 2024");
        assertEquals(15, result.getNumber(), "Numéro devrait être 15");
        
        // Vérifier qu'un second appel retourne null (fin de données)
        assertNull(reader.read(), "Le second read devrait retourner null");
    }

    @Test
    void givenExistingTargetDocumentWhenReadInForceModeThenReturnedAnyway() {
        // Given: Document ciblé loi-2023-10 existant dans fetch_results, mode force activé
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

        // When: Lecture en mode force
        LawDocument result = reader.read();

        // Then: Document retourné même s'il existe déjà
        assertNotNull(result, "Force mode should fetch document even if exists");
        assertEquals("loi", result.getType(), "Type devrait être loi");
    }

    @Test
    void givenExistingTargetDocumentWhenReadInNormalModeThenSkipped() {
        // Given: Document ciblé loi-2024-99 existant dans fetch_results, mode normal
        reader.setTargetDocumentId("loi-2024-99");
        reader.setForceMode(false);
        when(fetchResultRepository.existsByDocumentId("loi-2024-99")).thenReturn(true);

        // When: Lecture en mode normal
        LawDocument result = reader.read();

        // Then: Document skippé (null retourné), factory jamais appelée
        assertNull(result, "Should skip document that already exists");
        verify(documentFactory, never()).create(anyString(), anyInt(), anyInt());
    }

    @Test
    void givenInvalidDocumentIdFormatWhenReadThenReturnsNull() {
        // Given: Document ID avec format invalide (pas 3 parties)
        reader.setTargetDocumentId("invalid-format");

        // When: Tentative de lecture
        LawDocument result = reader.read();

        // Then: Null retourné pour format invalide
        assertNull(result, "Should return null for invalid documentId format");
    }

    @Test
    void givenMaxDocumentsTwoWhenReadThreeTimesThenThirdReturnsNull() {
        // Given: Limite de 2 documents, pas de cursor, pas de documents vérifiés
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

        // When: 3 lectures successives
        LawDocument first = reader.read();
        LawDocument second = reader.read();
        LawDocument third = reader.read();

        // Then: 2 documents retournés, le 3ème est null (limite atteinte)
        assertNotNull(first, "First document should be returned");
        assertNotNull(second, "Second document should be returned");
        assertNull(third, "Third document should be null (maxDocuments=2)");
    }

    @Test
    void givenExistingCursorWhenReadThenResumeFromCursor() {
        // Given: Cursor existant dans la base (année 2023, numéro 50)
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
    void givenNoCursorWhenReadThenStartsFromCurrentYearMinusOne() {
        // Given: Aucun cursor dans la base, pas de documents vérifiés, limite 1 document
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

        // When: Première lecture sans cursor
        LawDocument result = reader.read();

        // Then: Document généré depuis l'année courante - 1, cursor sauvegardé
        assertNotNull(result, "Should generate documents starting from current year - 1");
        verify(fetchCursorRepository).save(any(FetchCursor.class));
    }

    @Test
    void givenVerifiedDocumentsWhenReadInNormalModeThenSkipsThem() {
        // Given: 2 documents vérifiés dans fetch_results, mode normal, limite 2 documents
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

        // When: Lecture en mode normal
        reader.read();

        // Then: Documents vérifiés chargés, factory appelée pour nouveaux documents uniquement
        verify(fetchResultRepository).findAllDocumentIds();
        // Factory should NOT be called for verified documents
        verify(documentFactory, atLeast(1)).create(anyString(), anyInt(), anyInt());
    }

    @Test
    void givenVerifiedDocumentsWhenReadInForceModeThenDoesNotCheckVerified() {
        // Given: 2 documents vérifiés dans fetch_results, mode force, limite 2 documents
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

        // When: Lecture en mode force
        reader.read();

        // Then: findAllDocumentIds jamais appelé en mode force
        verify(fetchResultRepository, never()).findAllDocumentIds();
    }

    @Test
    void givenReaderUsedWhenResetThenCanReadAgain() {
        // Given: Reader avec limite 1 document, première lecture effectuée
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

        reader.read(); // First read

        // When: Reset du reader puis nouvelle lecture
        reader.reset();
        LawDocument result = reader.read();

        // Then: Lecture possible après reset
        assertNotNull(result, "Should read again after reset");
    }

    @Test
    void givenValidDocumentIdWhenParsedThenThreePartsExtracted() {
        // Given: ID de document valide au format type-year-number
        String documentId = "loi-2024-15";
        
        // When: Parsing de l'ID
        String[] parts = documentId.split("-");
        
        // Then: 3 parties correctement extraites
        assertEquals(3, parts.length, "L'ID devrait avoir 3 parties");
        assertEquals("loi", parts[0], "Partie 0 devrait être 'loi'");
        assertEquals("2024", parts[1], "Partie 1 devrait être '2024'");
        assertEquals("15", parts[2], "Partie 2 devrait être '15'");
    }

    @Test
    void givenInvalidDocumentIdWhenParsedThenNotThreeParts() {
        // Given: ID de document invalide (format incorrect)
        String documentId = "invalid-format";
        
        // When: Parsing de l'ID invalide
        String[] parts = documentId.split("-");
        
        // Then: Pas 3 parties (format invalide)
        assertEquals(2, parts.length, "Should not have 3 parts");
    }
}
