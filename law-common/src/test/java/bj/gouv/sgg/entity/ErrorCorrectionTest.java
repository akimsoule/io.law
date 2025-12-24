package bj.gouv.sgg.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCorrectionTest {

    @Test
    void prePersist_shouldTrimErrorFoundAndNormalizeCount() throws Exception {
        ErrorCorrection ec = ErrorCorrection.builder()
                .errorFound("  test-word  ")
                .errorCount(-5)
                .correctionText("corr")
                .correctionIsAutomatic(false)
                .build();

        // invoke private prePersist
        Method m = ErrorCorrection.class.getDeclaredMethod("prePersist");
        m.setAccessible(true);
        m.invoke(ec);

        assertThat(ec.getErrorFound()).isEqualTo("test-word");
        assertThat(ec.getErrorCount()).isEqualTo(0);
    }

    @Test
    void equalsAndHashCode_shouldUseIdWhenPresent() {
        ErrorCorrection a = new ErrorCorrection();
        ErrorCorrection b = new ErrorCorrection();

        a.setId(10L);
        b.setId(10L);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());

        // if id missing, equality should be false
        ErrorCorrection c = new ErrorCorrection();
        ErrorCorrection d = new ErrorCorrection();
        assertThat(c).isNotEqualTo(d);
    }
}
