package bj.gouv.sgg.reader;

import bj.gouv.sgg.batch.reader.CurrentYearLawDocumentReader;
import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchCursorRepository;
import bj.gouv.sgg.repository.FetchResultRepository;
import bj.gouv.sgg.service.CursorUpdateService;
import bj.gouv.sgg.util.LawDocumentFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CurrentYearLawDocumentReaderTest {

    @Mock
    private LawProperties properties;

    @Mock
    private FetchCursorRepository cursorRepository;

    @Mock
    private FetchResultRepository fetchResultRepository;

    @Mock
    private CursorUpdateService cursorUpdateService;

    private CurrentYearLawDocumentReader reader;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getBaseUrl()).thenReturn("https://sgg.gouv.bj/doc");
        lenient().when(properties.getMaxNumberPerYear()).thenReturn(10);
        
        // Utiliser le vrai LawDocumentFactory (pas de mock)
        LawDocumentFactory documentFactory = new LawDocumentFactory(properties);
        
        reader = new CurrentYearLawDocumentReader(properties, fetchResultRepository, cursorRepository, cursorUpdateService, documentFactory);
    }

    @Test
    void givenNoCursor_whenRead_thenGeneratesLawDocuments() {
        // Given - Mock cursorRepository pour retourner empty (démarre au début)
        lenient().when(cursorRepository.findByCursorTypeAndDocumentType("fetch-current", "loi"))
                .thenReturn(Optional.empty());
        lenient().when(cursorRepository.findByCursorTypeAndDocumentType("fetch-current", "decret"))
                .thenReturn(Optional.empty());
        
        // When - Lire tous les documents générés
        int count = 0;
        LawDocument lastDoc = null;
        LawDocument doc;
        while ((doc = reader.read()) != null && count < 100) {
            lastDoc = doc;
            count++;
        }

        // Then - Vérifier qu'au moins un document a été généré
        assertThat(count)
            .isGreaterThan(0)
            .isLessThanOrEqualTo(20); // 10 loi + 10 decret max
        if (lastDoc != null) {
            assertThat(lastDoc.getType()).isIn("loi", "decret");
            assertThat(lastDoc.getYear()).isPositive();
            assertThat(lastDoc.getNumber()).isPositive();
        }
    }

    @Test
    void givenAllDocumentsRead_whenReadAgain_thenReturnsNull() {
        // Given - Mock cursors et lecture de tous les documents
        lenient().when(cursorRepository.findByCursorTypeAndDocumentType("fetch-current", "loi"))
                .thenReturn(Optional.empty());
        lenient().when(cursorRepository.findByCursorTypeAndDocumentType("fetch-current", "decret"))
                .thenReturn(Optional.empty());
                
        int count = 0;
        while (reader.read() != null && count < 100) {
            count++;
        }

        // When - Lecture après épuisement
        LawDocument result = reader.read();

        // Then
        assertThat(result).isNull();
        assertThat(count).isLessThanOrEqualTo(20); // 10 loi + 10 decret max
    }
}
