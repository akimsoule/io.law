package bj.gouv.sgg.util;

import bj.gouv.sgg.exception.FileOperationException;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests complémentaires pour ErrorHandlingUtils
 * Couvre les méthodes non testées : executeWithFallback, executeOrNull, executeVoid, executeVoidSafely
 */
class ErrorHandlingUtilsComprehensiveTest {

    // ==================== executeWithFallback Tests ====================

    @Test
    void givenSuccessfulOperation_whenExecuteWithFallback_thenReturnsResult() {
        // Given
        Supplier<String> successOperation = () -> "success-value";
        String fallbackValue = "fallback-value";
        String operationType = "test-operation";
        String context = "test-context";

        // When
        String result = ErrorHandlingUtils.executeWithFallback(
            successOperation,
            fallbackValue,
            operationType,
            context
        );

        // Then
        assertThat(result).isEqualTo("success-value");
    }

    @Test
    void givenFailingOperation_whenExecuteWithFallback_thenReturnsFallbackValue() {
        // Given
        Supplier<String> failingOperation = () -> {
            throw new RuntimeException("operation failed");
        };
        String fallbackValue = "fallback-value";
        String operationType = "test-operation";
        String context = "test-context";

        // When
        String result = ErrorHandlingUtils.executeWithFallback(
            failingOperation,
            fallbackValue,
            operationType,
            context
        );

        // Then
        assertThat(result).isEqualTo("fallback-value");
    }

    @Test
    void givenNullFallback_whenExecuteWithFallback_thenReturnsNull() {
        // Given
        Supplier<String> failingOperation = () -> {
            throw new RuntimeException("operation failed");
        };
        String fallbackValue = null;
        String operationType = "test-operation";
        String context = "test-context";

        // When
        String result = ErrorHandlingUtils.executeWithFallback(
            failingOperation,
            fallbackValue,
            operationType,
            context
        );

        // Then
        assertThat(result).isNull();
    }

    @Test
    void givenComplexObjectOperation_whenExecuteWithFallback_thenReturnsComplexObject() {
        // Given
        Supplier<Integer> successOperation = () -> 42;
        Integer fallbackValue = 0;
        String operationType = "test-operation";
        String context = "test-context";

        // When
        Integer result = ErrorHandlingUtils.executeWithFallback(
            successOperation,
            fallbackValue,
            operationType,
            context
        );

        // Then
        assertThat(result).isEqualTo(42);
    }

    // ==================== executeOrNull Tests ====================

    @Test
    void givenSuccessfulOperation_whenExecuteOrNull_thenReturnsResult() {
        // Given
        Supplier<String> successOperation = () -> "test-value";
        String operationType = "test-operation";
        String context = "test-context";

        // When
        String result = ErrorHandlingUtils.executeOrNull(
            successOperation,
            operationType,
            context
        );

        // Then
        assertThat(result).isEqualTo("test-value");
    }

    @Test
    void givenFailingOperation_whenExecuteOrNull_thenReturnsNull() {
        // Given
        Supplier<String> failingOperation = () -> {
            throw new RuntimeException("operation failed");
        };
        String operationType = "test-operation";
        String context = "test-context";

        // When
        String result = ErrorHandlingUtils.executeOrNull(
            failingOperation,
            operationType,
            context
        );

        // Then
        assertThat(result).isNull();
    }

    // ==================== executeVoid Tests ====================

    @Test
    void givenSuccessfulVoidOperation_whenExecuteVoid_thenExecutes() {
        // Given
        boolean[] executed = {false};
        Runnable successOperation = () -> executed[0] = true;
        String operationType = "test-operation";
        String context = "test-context";

        // When
        ErrorHandlingUtils.executeVoid(
            successOperation,
            operationType,
            context
        );

        // Then
        assertThat(executed[0]).isTrue();
    }

    @Test
    void givenFailingVoidOperation_whenExecuteVoid_thenThrowsFileOperationException() {
        // Given
        Runnable failingOperation = () -> {
            throw new RuntimeException("operation failed");
        };
        String operationType = "test-operation";
        String context = "test-context";

        // When / Then
        assertThatThrownBy(() -> ErrorHandlingUtils.executeVoid(
            failingOperation,
            operationType,
            context
        ))
            .isInstanceOf(FileOperationException.class)
            .hasMessageContaining("test-operation")
            .hasMessageContaining("test-context");
    }

    // ==================== executeVoidSafely Tests ====================

    @Test
    void givenSuccessfulVoidOperation_whenExecuteVoidSafely_thenReturnsTrue() {
        // Given
        boolean[] executed = {false};
        Runnable successOperation = () -> executed[0] = true;
        String operationType = "test-operation";
        String context = "test-context";

        // When
        boolean result = ErrorHandlingUtils.executeVoidSafely(
            successOperation,
            operationType,
            context
        );

        // Then
        assertThat(result).isTrue();
        assertThat(executed[0]).isTrue();
    }

    @Test
    void givenFailingVoidOperation_whenExecuteVoidSafely_thenReturnsFalse() {
        // Given
        Runnable failingOperation = () -> {
            throw new RuntimeException("operation failed");
        };
        String operationType = "test-operation";
        String context = "test-context";

        // When
        boolean result = ErrorHandlingUtils.executeVoidSafely(
            failingOperation,
            operationType,
            context
        );

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void givenNullPointerException_whenExecuteVoidSafely_thenReturnsFalse() {
        // Given
        Runnable nullPointerOperation = () -> {
            String str = null;
            str.length(); // Will throw NPE
        };
        String operationType = "test-operation";
        String context = "test-context";

        // When
        boolean result = ErrorHandlingUtils.executeVoidSafely(
            nullPointerOperation,
            operationType,
            context
        );

        // Then
        assertThat(result).isFalse();
    }
}
