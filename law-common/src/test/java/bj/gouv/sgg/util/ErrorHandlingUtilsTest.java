package bj.gouv.sgg.util;

import bj.gouv.sgg.exception.FileOperationException;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitaires pour ErrorHandlingUtils.
 */
class ErrorHandlingUtilsTest {

    @Test
    void givenSuccessfulOperation_whenExecuteWithLogging_thenReturnsResult() {
        // Given
        Supplier<String> operation = () -> "success result";
        String operationName = "testOperation";
        String context = "test context";

        // When
        String result = ErrorHandlingUtils.executeWithLogging(operation, operationName, context);

        // Then
        assertThat(result).isEqualTo("success result");
    }

    @Test
    void givenFailingOperation_whenExecuteWithLogging_thenThrowsFileOperationException() {
        // Given
        Supplier<String> operation = () -> {
            throw new RuntimeException("Test error");
        };
        String operationName = "testOperation";
        String context = "test context";

        // When / Then
        assertThatThrownBy(() -> 
            ErrorHandlingUtils.executeWithLogging(operation, operationName, context)
        )
            .isInstanceOf(FileOperationException.class)
            .hasMessageContaining("[test context] testOperation failed")
            .hasCauseInstanceOf(RuntimeException.class)
            .hasRootCauseMessage("Test error");
    }

    @Test
    void givenNullPointerException_whenExecuteWithLogging_thenThrowsFileOperationException() {
        // Given
        Supplier<String> operation = () -> {
            throw new NullPointerException("Null value");
        };
        String operationName = "nullOperation";
        String context = "null context";

        // When / Then
        assertThatThrownBy(() -> 
            ErrorHandlingUtils.executeWithLogging(operation, operationName, context)
        )
            .isInstanceOf(FileOperationException.class)
            .hasMessageContaining("[null context] nullOperation failed")
            .hasCauseInstanceOf(NullPointerException.class);
    }

    @Test
    void givenIntegerOperation_whenExecuteWithLogging_thenReturnsInteger() {
        // Given
        Supplier<Integer> operation = () -> 42;
        String operationName = "intOperation";
        String context = "int context";

        // When
        Integer result = ErrorHandlingUtils.executeWithLogging(operation, operationName, context);

        // Then
        assertThat(result).isEqualTo(42);
    }

    @Test
    void givenNullReturningOperation_whenExecuteWithLogging_thenReturnsNull() {
        // Given
        Supplier<String> operation = () -> null;
        String operationName = "nullResultOperation";
        String context = "null result context";

        // When
        String result = ErrorHandlingUtils.executeWithLogging(operation, operationName, context);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void givenComplexObjectOperation_whenExecuteWithLogging_thenReturnsComplexObject() {
        // Given
        record TestData(String name, int value) {}
        TestData expectedData = new TestData("test", 123);
        Supplier<TestData> operation = () -> expectedData;

        // When
        TestData result = ErrorHandlingUtils.executeWithLogging(operation, "complexOperation", "complex context");

        // Then
        assertThat(result)
            .isNotNull()
            .extracting(TestData::name, TestData::value)
            .containsExactly("test", 123);
    }

    @Test
    void givenExceptionWithMessage_whenExecuteWithLogging_thenPreservesExceptionMessage() {
        // Given
        String errorMessage = "Specific error message with details";
        Supplier<String> operation = () -> {
            throw new IllegalArgumentException(errorMessage);
        };

        // When / Then
        assertThatThrownBy(() -> 
            ErrorHandlingUtils.executeWithLogging(operation, "operation", "context")
        )
            .isInstanceOf(FileOperationException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class)
            .hasRootCauseMessage(errorMessage);
    }

    @Test
    void givenMultipleExecutions_whenExecuteWithLogging_thenExecutesIndependently() {
        // Given
        int[] counter = {0};
        Supplier<Integer> operation = () -> ++counter[0];

        // When
        Integer result1 = ErrorHandlingUtils.executeWithLogging(operation, "op1", "ctx1");
        Integer result2 = ErrorHandlingUtils.executeWithLogging(operation, "op2", "ctx2");
        Integer result3 = ErrorHandlingUtils.executeWithLogging(operation, "op3", "ctx3");

        // Then
        assertThat(result1).isEqualTo(1);
        assertThat(result2).isEqualTo(2);
        assertThat(result3).isEqualTo(3);
    }
}
