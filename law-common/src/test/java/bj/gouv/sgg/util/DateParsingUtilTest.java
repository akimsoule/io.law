package bj.gouv.sgg.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour DateParsingUtil.
 */
class DateParsingUtilTest {

    @Test
    void givenValidFrenchDate_whenFormatDate_thenReturnsIsoFormat() {
        // Given
        String day = "15";
        String month = "janvier";
        String year = "2024";

        // When
        String result = DateParsingUtil.formatDate(day, month, year);

        // Then
        assertThat(result).isEqualTo("2024-01-15");
    }

    @Test
    void givenAccentlessFrenchMonth_whenFormatDate_thenReturnsIsoFormat() {
        // Given
        String day = "28";
        String month = "fevrier";  // Sans accent
        String year = "2024";

        // When
        String result = DateParsingUtil.formatDate(day, month, year);

        // Then
        assertThat(result).isEqualTo("2024-02-28");
    }

    @Test
    void givenAccentedFrenchMonth_whenFormatDate_thenReturnsIsoFormat() {
        // Given
        String day = "15";
        String month = "août";  // Avec accent
        String year = "2024";

        // When
        String result = DateParsingUtil.formatDate(day, month, year);

        // Then
        assertThat(result).isEqualTo("2024-08-15");
    }

    @Test
    void givenDecember_whenFormatDate_thenReturnsIsoFormat() {
        // Given
        String day = "31";
        String month = "décembre";
        String year = "2024";

        // When
        String result = DateParsingUtil.formatDate(day, month, year);

        // Then
        assertThat(result).isEqualTo("2024-12-31");
    }

    @Test
    void givenUnknownMonth_whenFormatDate_thenReturnsNull() {
        // Given
        String day = "15";
        String month = "invalid";
        String year = "2024";

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

    @Test
    void givenInvalidDay_whenFormatDate_thenReturnsNull() {
        // Given
        String day = "32";  // Invalid day
        String month = "janvier";
        String year = "2024";

        // When
        String result = DateParsingUtil.formatDate(day, month, year);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void givenInvalidYear_whenFormatDate_thenReturnsNull() {
        // Given
        String day = "15";
        String month = "janvier";
        String year = "invalid";

        // When
        String result = DateParsingUtil.formatDate(day, month, year);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void givenUppercaseMonth_whenFormatDate_thenHandlesCaseInsensitively() {
        // Given
        String day = "15";
        String month = "JANVIER";  // Uppercase
        String year = "2024";

        // When
        String result = DateParsingUtil.formatDate(day, month, year);

        // Then
        assertThat(result).isEqualTo("2024-01-15");
    }

    @Test
    void givenValidIsoDate_whenParseDate_thenReturnsLocalDate() {
        // Given
        String dateStr = "2024-01-15";

        // When
        LocalDate result = DateParsingUtil.parseDate(dateStr);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getYear()).isEqualTo(2024);
        assertThat(result.getMonthValue()).isEqualTo(1);
        assertThat(result.getDayOfMonth()).isEqualTo(15);
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
        assertThat(result).isNotNull();
        assertThat(result.getYear()).isEqualTo(2024);
    }

    @Test
    void givenInvalidFormat_whenParseDate_thenReturnsNull() {
        // Given
        String dateStr = "15/01/2024";  // Wrong format

        // When
        LocalDate result = DateParsingUtil.parseDate(dateStr);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void givenValidArrayAndIndex_whenParseDateFromArray_thenReturnsLocalDate() {
        // Given
        String[] parts = {"loi", "2024", "15", "2024-01-15"};
        int index = 3;

        // When
        LocalDate result = DateParsingUtil.parseDate(parts, index);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
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
