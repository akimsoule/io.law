package bj.gouv.sgg.orchestrator;

import bj.gouv.sgg.service.GenericOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrateur dédié au job `ocrJob`.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrOrchestrator {

    private final GenericOrchestrator genericOrchestrator;

    public void runOnce(String type, String documentId) throws Exception {
        genericOrchestrator.runOnce("ocrJob", type, documentId);
    }

    public void runContinuous(String type, String documentId, long intervalMillis, boolean stopOnFailure) {
        genericOrchestrator.runContinuous("ocrJob", type, documentId, intervalMillis, stopOnFailure);
    }
}
