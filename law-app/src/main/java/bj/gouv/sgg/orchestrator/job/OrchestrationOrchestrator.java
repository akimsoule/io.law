package bj.gouv.sgg.orchestrator.job;

import bj.gouv.sgg.service.OrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Orchestrateur dédié à l'orchestration continue (remplace --job=orchestrate via --orchestrator)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestrationOrchestrator {

    private final OrchestrationService orchestrationService;
    private final Environment env;

    public void runContinuous(String type, String documentId, long intervalMillis, boolean stopOnFailure) throws Exception {
        // Lire l'option skip-fetch-daily depuis l'environnement Spring (transmise par CLI comme --skip-fetch-daily)
        boolean skipFetchDaily = Boolean.parseBoolean(env.getProperty("skip-fetch-daily", "true"));
        log.info("▶️ Démarrage orchestrateur d'orchestration continue (type={}, skipFetchDaily={})", type, skipFetchDaily);
        orchestrationService.runContinuousOrchestration(type, skipFetchDaily);
    }

    public void runOnce(String type, String documentId) throws Exception {
        // Fournir un alias utile si besoin (exécute une itération de l'orchestration et s'arrête)
        orchestrationService.runContinuousOrchestration(type, true);
    }
}
