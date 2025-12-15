package bj.gouv.sgg.reader;

import bj.gouv.sgg.batch.reader.PreviousYearLawDocumentReader;
import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.FetchCursor;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchCursorRepository;
import bj.gouv.sgg.repository.FetchResultRepository;
import bj.gouv.sgg.service.CursorUpdateService;
import bj.gouv.sgg.service.NotFoundService;
import bj.gouv.sgg.util.LawDocumentFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Calendar;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PreviousYearsLawDocumentReaderTest {

    @Mock
    private LawProperties properties;
    
    @Mock
    private LawProperties.Batch batch;

    @Mock
    private FetchCursorRepository cursorRepository;

    @Mock
    private FetchResultRepository fetchResultRepository;

    @Mock
    private CursorUpdateService cursorUpdateService;

    @Mock
    private NotFoundService notFoundService;

    private PreviousYearLawDocumentReader reader;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getBaseUrl()).thenReturn("https://sgg.gouv.bj/doc");
        lenient().when(properties.getMaxNumberPerYear()).thenReturn(10);
        lenient().when(properties.getEndYear()).thenReturn(1960);
        lenient().when(properties.getBatch()).thenReturn(batch);
        lenient().when(batch.getMaxItemsToFetchPrevious()).thenReturn(100);
        
        // Mock notFoundRangeService pour éviter les appels réels
        lenient().when(notFoundService.isInNotFoundRange(
                org.mockito.ArgumentMatchers.anyString(), 
                org.mockito.ArgumentMatchers.anyInt(), 
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(false);
        
        // Mock fetchResultRepository pour indiquer qu'aucun document n'a déjà été traité
        lenient().when(fetchResultRepository.existsByDocumentIdAndNotRateLimited(
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(false);
        
        LawDocumentFactory documentFactory = new LawDocumentFactory(properties);
        reader = new PreviousYearLawDocumentReader(properties, fetchResultRepository, cursorRepository, cursorUpdateService, documentFactory, notFoundService);
    }

    @Test
    void givenNoCursor_whenRead_thenStartsFromPreviousYear() {
        // Given - Pas de curseur existant (démarre année-1)
        lenient().when(cursorRepository.findByCursorTypeAndDocumentType(
                FetchCursor.CURSOR_TYPE_FETCH_PREVIOUS, "loi"))
                .thenReturn(Optional.empty());
        lenient().when(cursorRepository.findByCursorTypeAndDocumentType(
                FetchCursor.CURSOR_TYPE_FETCH_PREVIOUS, "decret"))
                .thenReturn(Optional.empty());
        
        // When - Lire documents
        int count = 0;
        LawDocument lastDoc = null;
        LawDocument doc;
        while ((doc = reader.read()) != null && count < 30) {
            lastDoc = doc;
            count++;
        }

        // Then - Vérifie que des documents ont été générés pour année-1
        assertThat(count).isGreaterThan(0);
        if (lastDoc != null) {
            int expectedYear = Calendar.getInstance().get(Calendar.YEAR) - 1;
            assertThat(lastDoc.getYear()).isLessThanOrEqualTo(expectedYear);
            assertThat(lastDoc.getType()).isIn("loi", "decret");
        }
    }

    @Test
    void givenExistingCursor_whenRead_thenResumeFromCursor() {
        // Given - Curseur existant à 2020
        FetchCursor loiCursor = FetchCursor.builder()
                .documentType("loi")
                .currentYear(2020)
                .currentNumber(50)
                .build();
        FetchCursor decretCursor = FetchCursor.builder()
                .documentType("decret")
                .currentYear(2020)
                .currentNumber(100)
                .build();
        
        lenient().when(cursorRepository.findByCursorTypeAndDocumentType(
                FetchCursor.CURSOR_TYPE_FETCH_PREVIOUS, "loi"))
                .thenReturn(Optional.of(loiCursor));
        lenient().when(cursorRepository.findByCursorTypeAndDocumentType(
                FetchCursor.CURSOR_TYPE_FETCH_PREVIOUS, "decret"))
                .thenReturn(Optional.of(decretCursor));
        
        // When - Lire premier document
        LawDocument doc = reader.read();

        // Then - Doit reprendre depuis le curseur
        assertThat(doc).isNotNull();
        assertThat(doc.getYear()).isLessThanOrEqualTo(2020);
    }

    @Test
    void givenAllDocumentsRead_whenReadAgain_thenReturnsNull() {
        // Given - Curseur et lecture complète
        lenient().when(cursorRepository.findByCursorTypeAndDocumentType(
                FetchCursor.CURSOR_TYPE_FETCH_PREVIOUS, "loi"))
                .thenReturn(Optional.empty());
        lenient().when(cursorRepository.findByCursorTypeAndDocumentType(
                FetchCursor.CURSOR_TYPE_FETCH_PREVIOUS, "decret"))
                .thenReturn(Optional.empty());
        
        int count = 0;
        while (reader.read() != null && count < 100) {
            count++;
        }

        // When - Lecture après épuisement
        LawDocument result = reader.read();

        // Then
        assertThat(result).isNull();
    }
}
