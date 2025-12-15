package bj.gouv.sgg.processor;

import bj.gouv.sgg.batch.processor.CurrentYearLawDocumentProcessor;
import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.util.RateLimitHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FetchProcessorTest {

    @Mock
    private LawProperties properties;

    @Mock
    private RateLimitHandler rateLimitHandler;

    private CurrentYearLawDocumentProcessor processor;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getBaseUrl()).thenReturn("https://sgg.gouv.bj/doc");
        processor = new CurrentYearLawDocumentProcessor(properties, rateLimitHandler);
    }

    @Test
    void givenDocumentExists_whenProcess_thenMarkAsFetched() {
        // Given
        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2025)
            .number(1)
            .url("https://sgg.gouv.bj/doc/loi-2025-1")
            .build();

        when(rateLimitHandler.executeWithRetry(anyString(), any()))
            .thenReturn(200);

        // When
        LawDocument result = processor.process(document);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isExists()).isTrue();
        assertThat(result.getStatus()).isEqualTo(LawDocument.ProcessingStatus.FETCHED);
    }

    @Test
    void givenDocumentNotFound_whenProcess_thenMarkAsFailed() {
        // Given
        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2025)
            .number(999)
            .url("https://sgg.gouv.bj/doc/loi-2025-999")
            .build();

        when(rateLimitHandler.executeWithRetry(anyString(), any()))
            .thenReturn(404);

        // When
        LawDocument result = processor.process(document);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isExists()).isFalse();
        assertThat(result.getStatus()).isEqualTo(LawDocument.ProcessingStatus.FAILED);
    }

    @Test
    void givenRateLimited_whenProcess_thenMarkAsRateLimited() {
        // Given
        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2025)
            .number(5)
            .url("https://sgg.gouv.bj/doc/loi-2025-5")
            .build();

        when(rateLimitHandler.executeWithRetry(anyString(), any()))
            .thenReturn(429);

        // When
        LawDocument result = processor.process(document);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isExists()).isFalse();
        assertThat(result.getStatus()).isEqualTo(LawDocument.ProcessingStatus.RATE_LIMITED);
    }

    @Test
    void givenSingleDigitNumber_whenProcess_thenTriesPaddedFormat() {
        // Given
        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2025)
            .number(5)
            .url("https://sgg.gouv.bj/doc/loi-2025-5")
            .build();

        // Premier appel (sans padding) → 404
        // Deuxième appel (avec padding) → 200
        when(rateLimitHandler.executeWithRetry(anyString(), any()))
            .thenReturn(404)  // loi-2025-5
            .thenReturn(200); // loi-2025-05

        // When
        LawDocument result = processor.process(document);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isExists()).isTrue();
        assertThat(result.getStatus()).isEqualTo(LawDocument.ProcessingStatus.FETCHED);
        assertThat(result.getUrl()).contains("-05"); // URL mise à jour avec padding
    }
}
