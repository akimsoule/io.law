package bj.gouv.sgg.orchestrator;

import bj.gouv.sgg.job.JobOrchestrator;
import bj.gouv.sgg.service.GenericOrchestrator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GenericOrchestratorTest {

    @Test
    void runOnce_delegatesToJobOrchestrator() throws Exception {
        JobOrchestrator jobOrchestrator = Mockito.mock(JobOrchestrator.class);
        GenericOrchestrator generic = new GenericOrchestrator(jobOrchestrator);

        // should not throw
        generic.runOnce("downloadJob", "loi", "ABC-1");

        verify(jobOrchestrator, times(1)).runJob(eq("downloadJob"), Mockito.anyMap());
    }

    @Test
    void runContinuous_stopOnFailureTrue_propagatesAsIllegalState() throws Exception {
        JobOrchestrator jobOrchestrator = Mockito.mock(JobOrchestrator.class);
        // force an exception on first run
        Mockito.doThrow(new RuntimeException("boom")).when(jobOrchestrator).runJob(eq("pdfToImagesJob"), Mockito.anyMap());

        GenericOrchestrator generic = new GenericOrchestrator(jobOrchestrator);

        assertThrows(IllegalStateException.class, () -> {
            generic.runContinuous("pdfToImagesJob", "loi", "ALL", 10, true);
        });
    }
}
