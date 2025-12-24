package bj.gouv.sgg.service.correction;

import bj.gouv.sgg.service.correction.impl.LinguisticCorrectionImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinguisticCorrectionIntegrationTest {

    @Test
    void givenMisspeltWordWhenGetSafeSuggestionThenReturnsSuggestionAndPreservesCase() {
        LinguisticCorrectionImpl lc = new LinguisticCorrectionImpl();

        String suggestionLower = lc.getSafeSuggestion("ministàre");
        assertThat(suggestionLower).isNotNull();
        assertThat(suggestionLower).isNotEqualToIgnoringCase("ministàre");
        assertThat(suggestionLower.length()).isGreaterThan(3);

        String suggestionCap = lc.getSafeSuggestion("Ministàre");
        assertThat(suggestionCap).isNotNull();
        assertThat(suggestionCap).startsWith("M");
        // capitalized replacement should preserve capitalization of the first letter
        assertThat(suggestionCap).isEqualTo(
                Character.toUpperCase(suggestionLower.charAt(0)) + suggestionLower.substring(1));
    }

    @Test
    void givenAcronymOrShortWordWhenGetSafeSuggestionThenReturnsNull() {
        LinguisticCorrectionImpl lc = new LinguisticCorrectionImpl();

        // Acronym in upper-case should be ignored
        assertThat(lc.getSafeSuggestion("USA")).isNull();

        // Very short words are ignored by implementation
        assertThat(lc.getSafeSuggestion("ab")).isNull();
        assertThat(lc.getSafeSuggestion("une")).isNull(); // length 3 -> ignored
    }
}
