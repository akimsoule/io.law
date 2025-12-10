package bj.gouv.sgg.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests pour PatternCache
 * Cache thread-safe pour patterns regex compilés
 */
class PatternCacheTest {

    @AfterEach
    void tearDown() {
        // Clean cache après chaque test
        PatternCache.clear();
    }

    // ==================== get(String, int) Tests ====================

    @Test
    void givenRegexWithFlags_whenGet_thenReturnsCompiledPattern() {
        // Given
        String regex = "test\\d+";
        int flags = Pattern.CASE_INSENSITIVE;

        // When
        Pattern pattern = PatternCache.get(regex, flags);

        // Then
        assertThat(pattern).isNotNull();
        assertThat(pattern.pattern()).isEqualTo(regex);
        assertThat(pattern.flags() & Pattern.CASE_INSENSITIVE).isNotZero();
    }

    @Test
    void givenSameRegexAndFlags_whenGetMultipleTimes_thenReturnsSameInstance() {
        // Given
        String regex = "test\\d+";
        int flags = Pattern.CASE_INSENSITIVE;

        // When
        Pattern pattern1 = PatternCache.get(regex, flags);
        Pattern pattern2 = PatternCache.get(regex, flags);

        // Then
        assertThat(pattern1).isSameAs(pattern2); // Même instance
    }

    @Test
    void givenSameRegexWithDifferentFlags_whenGet_thenReturnsDifferentPatterns() {
        // Given
        String regex = "test\\d+";
        int flags1 = Pattern.CASE_INSENSITIVE;
        int flags2 = Pattern.MULTILINE;

        // When
        Pattern pattern1 = PatternCache.get(regex, flags1);
        Pattern pattern2 = PatternCache.get(regex, flags2);

        // Then
        assertThat(pattern1).isNotSameAs(pattern2);
        assertThat(pattern1.flags()).isNotEqualTo(pattern2.flags());
    }

    @Test
    void givenRegexWithNoFlags_whenGet_thenReturnsPattern() {
        // Given
        String regex = "simple";
        int flags = 0;

        // When
        Pattern pattern = PatternCache.get(regex, flags);

        // Then
        assertThat(pattern).isNotNull();
        assertThat(pattern.pattern()).isEqualTo(regex);
    }

    // ==================== get(String) Tests ====================

    @Test
    void givenRegexOnly_whenGet_thenReturnsPatternWithDefaultFlags() {
        // Given
        String regex = "test\\d+";

        // When
        Pattern pattern = PatternCache.get(regex);

        // Then
        assertThat(pattern).isNotNull();
        assertThat(pattern.pattern()).isEqualTo(regex);
    }

    @Test
    void givenSameRegexOnly_whenGetMultipleTimes_thenCachesPattern() {
        // Given
        String regex = "test\\d+";

        // When
        Pattern pattern1 = PatternCache.get(regex);
        Pattern pattern2 = PatternCache.get(regex);

        // Then
        assertThat(pattern1).isSameAs(pattern2);
    }

    @Test
    void givenDifferentRegexPatterns_whenGet_thenReturnsDifferentPatterns() {
        // Given
        String regex1 = "pattern1";
        String regex2 = "pattern2";

        // When
        Pattern pattern1 = PatternCache.get(regex1);
        Pattern pattern2 = PatternCache.get(regex2);

        // Then
        assertThat(pattern1).isNotSameAs(pattern2);
        assertThat(pattern1.pattern()).isEqualTo(regex1);
        assertThat(pattern2.pattern()).isEqualTo(regex2);
    }

    // ==================== preload Tests ====================

    @Test
    void givenPatternKey_whenPreload_thenCachesPattern() {
        // Given
        String key = "article-pattern";
        String regex = "Article\\s+(\\d+)";
        int flags = Pattern.CASE_INSENSITIVE;

        // When
        PatternCache.preload(key, regex, flags);
        Pattern pattern = PatternCache.get(regex, flags);

        // Then
        assertThat(pattern).isNotNull();
        assertThat(pattern.pattern()).isEqualTo(regex);
    }

    @Test
    void givenMultiplePatternKeys_whenPreload_thenCachesAllPatterns() {
        // Given
        String key1 = "date-pattern";
        String regex1 = "\\d{2}/\\d{2}/\\d{4}";
        String key2 = "email-pattern";
        String regex2 = "[a-z]+@[a-z]+\\.[a-z]+";

        // When
        PatternCache.preload(key1, regex1, 0);
        PatternCache.preload(key2, regex2, Pattern.CASE_INSENSITIVE);

        Pattern pattern1 = PatternCache.get(regex1, 0);
        Pattern pattern2 = PatternCache.get(regex2, Pattern.CASE_INSENSITIVE);

        // Then
        assertThat(pattern1.pattern()).isEqualTo(regex1);
        assertThat(pattern2.pattern()).isEqualTo(regex2);
    }

    // ==================== getCacheSize Tests ====================

    @Test
    void givenEmptyCache_whenGetCacheSize_thenReturnsZero() {
        // Given
        // Cache cleared in @AfterEach

        // When
        int size = PatternCache.getCacheSize();

        // Then
        assertThat(size).isZero();
    }

    @Test
    void givenMultipleCachedPatterns_whenGetCacheSize_thenReturnsCorrectSize() {
        // Given
        PatternCache.get("pattern1");
        PatternCache.get("pattern2");
        PatternCache.get("pattern3", Pattern.CASE_INSENSITIVE);

        // When
        int size = PatternCache.getCacheSize();

        // Then
        assertThat(size).isEqualTo(3);
    }

    @Test
    void givenDuplicateCachedPatterns_whenGetCacheSize_thenDoesNotIncrease() {
        // Given
        PatternCache.get("pattern1");
        PatternCache.get("pattern1"); // Duplicate
        PatternCache.get("pattern1"); // Duplicate

        // When
        int size = PatternCache.getCacheSize();

        // Then
        assertThat(size).isEqualTo(1); // Only one unique pattern
    }

    // ==================== clear Tests ====================

    @Test
    void givenCachedPatterns_whenClear_thenEmptiesCache() {
        // Given
        PatternCache.get("pattern1");
        PatternCache.get("pattern2");
        int sizeBeforeClear = PatternCache.getCacheSize();

        // When
        PatternCache.clear();
        int sizeAfterClear = PatternCache.getCacheSize();

        // Then
        assertThat(sizeBeforeClear).isEqualTo(2);
        assertThat(sizeAfterClear).isZero();
    }

    @Test
    void givenClearedCache_whenGet_thenAllowsNewCaching() {
        // Given
        PatternCache.get("pattern1");
        PatternCache.clear();

        // When
        Pattern pattern = PatternCache.get("pattern2");
        int size = PatternCache.getCacheSize();

        // Then
        assertThat(pattern).isNotNull();
        assertThat(size).isEqualTo(1);
    }

    // ==================== Thread Safety Tests ====================

    @Test
    void givenConcurrentAccess_whenGet_thenThreadSafe() throws InterruptedException {
        // Given
        String regex = "concurrent\\d+";
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        // When
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                Pattern pattern = PatternCache.get(regex);
                assertThat(pattern).isNotNull();
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Then
        int cacheSize = PatternCache.getCacheSize();
        assertThat(cacheSize).isEqualTo(1); // Should only cache once
    }
}
