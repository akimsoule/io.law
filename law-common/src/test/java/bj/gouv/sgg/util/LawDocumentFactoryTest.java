package bj.gouv.sgg.util;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.model.LawDocument.ProcessingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour LawDocumentFactory.
 */
@ExtendWith(MockitoExtension.class)
class LawDocumentFactoryTest {

    @Mock
    private LawProperties lawProperties;

    private LawDocumentFactory factory;

    @BeforeEach
    void setUp() {
        when(lawProperties.getBaseUrl()).thenReturn("https://sgg.gouv.bj/doc");
        factory = new LawDocumentFactory(lawProperties);
    }

    @Test
    void givenLoiType_whenCreate_thenReturnsValidLawDocument() {
        // Given
        String type = "loi";
        int year = 2024;
        int number = 15;

        // When
        LawDocument result = factory.create(type, year, number);

        // Then
        assertThat(result)
            .isNotNull()
            .satisfies(doc -> {
                assertThat(doc.getType()).isEqualTo("loi");
                assertThat(doc.getYear()).isEqualTo(2024);
                assertThat(doc.getNumber()).isEqualTo(15);
                assertThat(doc.getStatus()).isEqualTo(ProcessingStatus.PENDING);
                assertThat(doc.getUrl()).isEqualTo("https://sgg.gouv.bj/doc/loi-2024-15");
                assertThat(doc.getDocumentId()).isEqualTo("loi-2024-15");
            });
    }

    @Test
    void givenDecretType_whenCreate_thenReturnsValidDecretDocument() {
        // Given
        String type = "decret";
        int year = 2025;
        int number = 42;

        // When
        LawDocument result = factory.create(type, year, number);

        // Then
        assertThat(result)
            .isNotNull()
            .satisfies(doc -> {
                assertThat(doc.getType()).isEqualTo("decret");
                assertThat(doc.getYear()).isEqualTo(2025);
                assertThat(doc.getNumber()).isEqualTo(42);
                assertThat(doc.getStatus()).isEqualTo(ProcessingStatus.PENDING);
                assertThat(doc.getUrl()).isEqualTo("https://sgg.gouv.bj/doc/decret-2025-42");
                assertThat(doc.getDocumentId()).isEqualTo("decret-2025-42");
            });
    }

    @Test
    void givenDifferentYears_whenCreate_thenGeneratesCorrectYearInUrl() {
        // Given
        String type = "loi";
        int number = 1;

        // When
        LawDocument doc2024 = factory.create(type, 2024, number);
        LawDocument doc2023 = factory.create(type, 2023, number);
        LawDocument doc1960 = factory.create(type, 1960, number);

        // Then
        assertThat(doc2024.getYear()).isEqualTo(2024);
        assertThat(doc2024.getUrl()).isEqualTo("https://sgg.gouv.bj/doc/loi-2024-1");
        
        assertThat(doc2023.getYear()).isEqualTo(2023);
        assertThat(doc2023.getUrl()).isEqualTo("https://sgg.gouv.bj/doc/loi-2023-1");
        
        assertThat(doc1960.getYear()).isEqualTo(1960);
        assertThat(doc1960.getUrl()).isEqualTo("https://sgg.gouv.bj/doc/loi-1960-1");
    }

    @Test
    void givenLargeNumber_whenCreate_thenHandlesLargeNumberCorrectly() {
        // Given
        String type = "loi";
        int year = 2024;
        int number = 1999;

        // When
        LawDocument result = factory.create(type, year, number);

        // Then
        assertThat(result.getNumber()).isEqualTo(1999);
        assertThat(result.getUrl()).isEqualTo("https://sgg.gouv.bj/doc/loi-2024-1999");
    }

    @Test
    void givenAnyParameters_whenCreate_thenStatusIsAlwaysPending() {
        // Given
        String type = "loi";
        int year = 2024;

        // When
        LawDocument doc1 = factory.create(type, year, 1);
        LawDocument doc2 = factory.create(type, year, 2);
        LawDocument doc3 = factory.create(type, year, 3);

        // Then
        assertThat(doc1.getStatus()).isEqualTo(ProcessingStatus.PENDING);
        assertThat(doc2.getStatus()).isEqualTo(ProcessingStatus.PENDING);
        assertThat(doc3.getStatus()).isEqualTo(ProcessingStatus.PENDING);
    }

    @Test
    void givenCustomBaseUrl_whenCreate_thenUsesCustomBaseUrl() {
        // Given
        when(lawProperties.getBaseUrl()).thenReturn("https://custom.example.com");
        LawDocumentFactory customFactory = new LawDocumentFactory(lawProperties);
        
        String type = "loi";
        int year = 2024;
        int number = 1;

        // When
        LawDocument result = customFactory.create(type, year, number);

        // Then
        assertThat(result.getUrl()).isEqualTo("https://custom.example.com/loi-2024-1");
    }

    @Test
    void givenValidParameters_whenCreate_thenUrlMatchesExpectedFormat() {
        // Given
        String type = "loi";
        int year = 2024;
        int number = 15;

        // When
        LawDocument result = factory.create(type, year, number);

        // Then
        assertThat(result.getUrl()).matches("https://sgg\\.gouv\\.bj/doc/loi-\\d{4}-\\d+");
    }

    @Test
    void givenSameParameters_whenCreateMultipleTimes_thenGeneratesConsistentUrl() {
        // Given
        String type = "decret";
        int year = 2023;
        int number = 100;

        // When
        LawDocument doc1 = factory.create(type, year, number);
        LawDocument doc2 = factory.create(type, year, number);

        // Then
        assertThat(doc1.getUrl())
            .isEqualTo(doc2.getUrl())
            .isEqualTo("https://sgg.gouv.bj/doc/decret-2023-100");
    }
}
