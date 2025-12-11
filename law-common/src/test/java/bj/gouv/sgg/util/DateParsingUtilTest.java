package bj.gouv.sgg.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour DateParsingUtil.
 */
class DateParsingUtilTest {

    @ParameterizedTest
    @CsvSource({
        "15, janvier, 2024, 2024-01-15",
        "28, fevrier, 2024, 2024-02-28",
        "15, août, 2024, 2024-08-15",
        "31, décembre, 2024, 2024-12-31",
        "15, JANVIER, 2024, 2024-01-15",
        "1, janvier, 2024, 2024-01-01",
        "1, février, 2024, 2024-02-01",
        "1, mars, 2024, 2024-03-01",
        "1, avril, 2024, 2024-04-01",
        "1, mai, 2024, 2024-05-01",
        "1, juin, 2024, 2024-06-01",
        "1, juillet, 2024, 2024-07-01",
        "1, août, 2024, 2024-08-01",
        "1, septembre, 2024, 2024-09-01",
        "1, octobre, 2024, 2024-10-01",
        "1, novembre, 2024, 2024-11-01",
        "1, décembre, 2024, 2024-12-01",
        "29, février, 2024, 2024-02-29"
    })
    void givenValidFrenchDate_whenFormatDate_thenReturnsIsoFormat(String day, String month, String year, String expected) {
        // When
        String result = DateParsingUtil.formatDate(day, month, year);

        // Then
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "15, invalid, 2024",
        "32, janvier, 2024",
        "15, janvier, invalid",
        "29, février, 2023"
    })
    void givenInvalidDate_whenFormatDate_thenReturnsNull(String day, String month, String year) {
        // When
        String result = DateParsingUtil.formatDate(day, month, year);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void givenNullValues_whenFormatDate_thenReturnsNull() {
        // When / Then
        assertThat(DateParsingUtil.formatDate(null, "janvier", "2024")).isNull();
        assertThat(DateParsingUtil.formatDate("15", null, "2024")).isNull();
        assertThat(DateParsingUtil.formatDate("15", "janvier", null)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
        "2024-01-15, 2024, 1, 15",
        "  2024-01-15  , 2024, 1, 15"
    })
    void givenValidIsoDate_whenParseDate_thenReturnsLocalDate(String dateStr, int expectedYear, int expectedMonth, int expectedDay) {
        // When
        LocalDate result = DateParsingUtil.parseDate(dateStr);

        // Then
        assertThat(result)
            .isNotNull()
            .satisfies(date -> {
                assertThat(date.getYear()).isEqualTo(expectedYear);
                assertThat(date.getMonthValue()).isEqualTo(expectedMonth);
                assertThat(date.getDayOfMonth()).isEqualTo(expectedDay);
            });
    }

    @Test
    void givenNullValue_whenParseDate_thenReturnsNull() {
        // When
        LocalDate result = DateParsingUtil.parseDate(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void givenEmptyString_whenParseDate_thenReturnsNull() {
        // When
        LocalDate result = DateParsingUtil.parseDate("");

        // Then
        assertThat(result).isNull();
    }

    @Test
    void givenWhitespaceAroundDate_whenParseDate_thenTrimsAndParses() {
        // Given
        String dateStr = "  2024-01-15  ";

        // When
        LocalDate result = DateParsingUtil.parseDate(dateStr);

        // Then
        assertThat(result)
            .isNotNull()
            .hasYear(2024);
    }

    @ParameterizedTest
    @ValueSource(strings = {"15/01/2024", "invalid-date", "2024-13-01", "2024-01-32"})
    void givenInvalidDateFormat_whenParseDate_thenReturnsNull(String dateStr) {
        assertThat(DateParsingUtil.parseDate(dateStr)).isNull();
    }

    @Test
    void givenValidArrayAndIndex_whenParseDateFromArray_thenReturnsLocalDate() {
        // Given
        String[] parts = {"loi", "2024", "15", "2024-01-15"};
        int index = 3;

        // When
        LocalDate result = DateParsingUtil.parseDate(parts, index);

        // Then
        assertThat(result)
            .isNotNull()
            .isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    void givenOutOfBoundsIndex_whenParseDateFromArray_thenReturnsNull() {
        // Given
        String[] parts = {"loi", "2024", "15"};
        int index = 5;  // Out of bounds

        // When
        LocalDate result = DateParsingUtil.parseDate(parts, index);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void givenNullArray_whenParseDateFromArray_thenReturnsNull() {
        // When
        LocalDate result = DateParsingUtil.parseDate(null, 0);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void givenAllFrenchMonths_whenFormatDate_thenReturnsCorrectIsoFormat() {
        // Test all months
        assertThat(DateParsingUtil.formatDate("1", "janvier", "2024")).isEqualTo("2024-01-01");
        assertThat(DateParsingUtil.formatDate("1", "février", "2024")).isEqualTo("2024-02-01");
        assertThat(DateParsingUtil.formatDate("1", "mars", "2024")).isEqualTo("2024-03-01");
        assertThat(DateParsingUtil.formatDate("1", "avril", "2024")).isEqualTo("2024-04-01");
        assertThat(DateParsingUtil.formatDate("1", "mai", "2024")).isEqualTo("2024-05-01");
        assertThat(DateParsingUtil.formatDate("1", "juin", "2024")).isEqualTo("2024-06-01");
        assertThat(DateParsingUtil.formatDate("1", "juillet", "2024")).isEqualTo("2024-07-01");
        assertThat(DateParsingUtil.formatDate("1", "août", "2024")).isEqualTo("2024-08-01");
        assertThat(DateParsingUtil.formatDate("1", "septembre", "2024")).isEqualTo("2024-09-01");
        assertThat(DateParsingUtil.formatDate("1", "octobre", "2024")).isEqualTo("2024-10-01");
        assertThat(DateParsingUtil.formatDate("1", "novembre", "2024")).isEqualTo("2024-11-01");
        assertThat(DateParsingUtil.formatDate("1", "décembre", "2024")).isEqualTo("2024-12-01");
    }

    @Test
    void givenLeapYearFebruary29_whenFormatDate_thenReturnsValidDate() {
        // Given - 2024 is a leap year
        String day = "29";
        String month = "février";
        String year = "2024";

        // When
        String result = DateParsingUtil.formatDate(day, month, year);

        // Then
        assertThat(result).isEqualTo("2024-02-29");
    }

    @Test
    void givenNonLeapYearFebruary29_whenFormatDate_thenReturnsNull() {
        // Given - 2023 is not a leap year
        String day = "29";
        String month = "février";
        String year = "2023";

        // When
        String result = DateParsingUtil.formatDate(day, month, year);

        // Then
        assertThat(result).isNull();  // Invalid date
    }
}
