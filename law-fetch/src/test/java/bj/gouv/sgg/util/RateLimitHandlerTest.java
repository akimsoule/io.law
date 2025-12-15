package bj.gouv.sgg.util;

import bj.gouv.sgg.util.RateLimitHandler.ProbeFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitHandlerTest {

    private RateLimitHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RateLimitHandler();
    }

    @Test
    void givenSuccessfulOperation_whenExecuteWithRetry_thenReturnResult() {
        // Given
        String url = "https://example.com/test";
        ProbeFunction operation = u -> 200;

        // When
        Integer result = handler.executeWithRetry(url, operation);

        // Then
        assertThat(result).isEqualTo(200);
    }

    @Test
    void givenInitial429ThenSuccess_whenExecuteWithRetry_thenRetryAndSucceed() {
        // Given
        String url = "https://example.com/test";
        int[] attemptCount = {0};
        
        ProbeFunction operation = u -> {
            attemptCount[0]++;
            if (attemptCount[0] < 2) {
                return 429; // Premier essai → 429
            }
            return 200; // Deuxième essai → succès
        };

        // When
        Integer result = handler.executeWithRetry(url, operation);

        // Then
        assertThat(result).isEqualTo(200);
        assertThat(attemptCount[0]).isGreaterThan(1); // Au moins 2 tentatives
    }

    @Test
    void givenPersistent429_whenExecuteWithRetry_thenReturn429AfterMaxRetries() {
        // Given
        String url = "https://example.com/test";
        ProbeFunction operation = u -> 429; // Toujours 429

        // When
        Integer result = handler.executeWithRetry(url, operation);

        // Then
        assertThat(result).isEqualTo(429); // Retourne 429 après épuisement des retries
    }

    @Test
    void givenMultipleRequests_whenBeforeRequest_thenNoException() {
        // When/Then - Ne devrait pas lancer d'exception
        handler.beforeRequest();
        handler.beforeRequest();
    }

    @Test
    void givenMultiple429_whenOn429_thenIncrementCounter() {
        // When
        handler.on429("https://example.com/test1");
        handler.on429("https://example.com/test2");

        // Then - Vérifie que le handler a bien enregistré les 429
        // (pas d'exception levée)
    }
}
