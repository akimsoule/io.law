package bj.gouv.sgg.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour RateLimitHandler
 */
class RateLimitHandlerTest {

    private RateLimitHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RateLimitHandler();
    }

    @Test
    void givenNewInstanceWhenCreateRateLimitHandlerThenInitializesSuccessfully() {
        // Given & When
        RateLimitHandler newHandler = new RateLimitHandler();

        // Then
        assertNotNull(newHandler);
    }

    @Test
    void givenInitializedHandlerWhenBeforeRequestThenDoesNotThrow() {
        // When & Then
        assertDoesNotThrow(() -> handler.beforeRequest());
    }

    @Test
    void givenRateLimitErrorWhenOn429ThenDoesNotThrow() {
        // When & Then
        assertDoesNotThrow(() -> handler.on429("https://sgg.gouv.bj/doc/loi-2024-15"));
    }

    @Test
    void givenMultipleRateLimitsWhenOn429ThenAdaptsDelay() {
        // Given & When - Simuler plusieurs 429
        handler.on429("url1");
        handler.on429("url2");
        handler.on429("url3");

        // Then - Ne devrait pas lever d'exception
        assertDoesNotThrow(() -> handler.beforeRequest());
    }

    @Test
    void givenMultipleRequestsWhenBeforeRequestThenCompletesInReasonableTime() {
        // Given & When
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            handler.beforeRequest();
        }
        long duration = System.currentTimeMillis() - startTime;

        // Then - Devrait être raisonnablement rapide
        assertTrue(duration < 5000, "Requests should not block for too long");
    }

    @Test
    void givenManyCallsWhenBeforeRequestThenAllSucceed() {
        // When & Then - Plusieurs appels ne doivent pas échouer
        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(() -> handler.beforeRequest());
        }
    }

    @Test
    void givenConsecutiveRateLimitsWhenOn429ThenRemainsFunctional() {
        // Given & When - Appels consécutifs de 429
        for (int i = 0; i < 10; i++) {
            handler.on429("https://sgg.gouv.bj/doc/loi-2024-" + i);
        }

        // Then - Handler doit rester fonctionnel
        assertDoesNotThrow(() -> handler.beforeRequest());
    }
}
