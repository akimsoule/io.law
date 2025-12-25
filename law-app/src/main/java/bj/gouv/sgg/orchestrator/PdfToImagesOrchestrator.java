package bj.gouv.sgg.orchestrator;

import bj.gouv.sgg.service.GenericOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrateur dédié au job `pdfToImagesJob`.
 * Fournit une exécution unique (runOnce) et une exécution continue (runContinuous)
 * avec option d'arrêt sur échec.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfToImagesOrchestrator {

    private final GenericOrchestrator genericOrchestrator;

    /**
     * Exécute une seule fois le job `pdfToImagesJob`.
     * @param type "loi" ou "decret"
     * @param documentId ID du document ou null pour "ALL"
     * @throws Exception si le job échoue
     */
    public void runOnce(String type, String documentId) throws Exception {
        genericOrchestrator.runOnce("pdfToImagesJob", type, documentId);
    }

    /**
     * Exécute en boucle le job `pdfToImagesJob`.
     * @param type type de document
     * @param documentId id du document ou null pour ALL
     * @param intervalMillis pause entre deux exécutions en millisecondes
     * @param stopOnFailure si true, arrête la boucle si une exécution échoue
     */
    public void runContinuous(String type, String documentId, long intervalMillis, boolean stopOnFailure) {
        genericOrchestrator.runContinuous("pdfToImagesJob", type, documentId, intervalMillis, stopOnFailure);
    }
}
