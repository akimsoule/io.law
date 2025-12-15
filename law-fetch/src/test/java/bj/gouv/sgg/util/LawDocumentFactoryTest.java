package bj.gouv.sgg.util;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.LawDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LawDocumentFactoryTest {

    @Mock
    private LawProperties properties;

    private LawDocumentFactory factory;

    @BeforeEach
    void setUp() {
        when(properties.getBaseUrl()).thenReturn("https://sgg.gouv.bj/doc");
        factory = new LawDocumentFactory(properties);
    }

    @Test
    void givenValidParameters_whenCreate_thenReturnsDocumentWithUrl() {
        // Given
        String type = "loi";
        int year = 2024;
        int number = 15;

        // When
        LawDocument document = factory.create(type, year, number);

        // Then
        assertThat(document).isNotNull();
        assertThat(document.getType()).isEqualTo("loi");
        assertThat(document.getYear()).isEqualTo(2024);
        assertThat(document.getNumber()).isEqualTo(15);
        assertThat(document.getUrl()).isEqualTo("https://sgg.gouv.bj/doc/loi-2024-15");
        assertThat(document.getStatus()).isEqualTo(LawDocument.ProcessingStatus.PENDING);
    }

    @Test
    void givenDecretType_whenCreate_thenBuildsCorrectUrl() {
        // Given
        String type = "decret";
        int year = 2023;
        int number = 100;

        // When
        LawDocument document = factory.create(type, year, number);

        // Then
        assertThat(document.getUrl()).isEqualTo("https://sgg.gouv.bj/doc/decret-2023-100");
        assertThat(document.getType()).isEqualTo("decret");
    }

    @Test
    void givenSingleDigitNumber_whenCreate_thenBuildsUrlWithoutPadding() {
        // Given
        String type = "loi";
        int year = 2022;
        int number = 5;

        // When
        LawDocument document = factory.create(type, year, number);

        // Then
        assertThat(document.getUrl()).isEqualTo("https://sgg.gouv.bj/doc/loi-2022-5");
        assertThat(document.getNumber()).isEqualTo(5);
    }

    @Test
    void givenOldYear_whenCreate_thenBuildsCorrectUrl() {
        // Given
        String type = "loi";
        int year = 1960;
        int number = 1;

        // When
        LawDocument document = factory.create(type, year, number);

        // Then
        assertThat(document.getUrl()).isEqualTo("https://sgg.gouv.bj/doc/loi-1960-1");
        assertThat(document.getYear()).isEqualTo(1960);
    }

    @Test
    void givenAnyParameters_whenCreate_thenStatusIsPending() {
        // Given
        String type = "decret";
        int year = 2021;
        int number = 50;

        // When
        LawDocument document = factory.create(type, year, number);

        // Then
        assertThat(document.getStatus()).isEqualTo(LawDocument.ProcessingStatus.PENDING);
    }
}
