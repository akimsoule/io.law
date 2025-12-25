package bj.gouv.sgg.service;

import bj.gouv.sgg.job.JobOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Orchestrateur générique réutilisé par les orchestrateurs spécifiques.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenericOrchestrator {

    private final JobOrchestrator orchestrator;

    public void runOnce(String jobName, String type, String documentId) throws Exception {
        String docId = documentId == null ? "ALL" : documentId;
        Map<String, String> params = Map.of(
            "type", type == null ? "loi" : type,
            "documentId", docId
        );

        log.info("▶️ Lancement unique de '{}' pour type={} documentId={}", jobName, type, docId);
        orchestrator.runJob(jobName, params);
        log.info("✅ '{}' terminé avec succès pour documentId={}", jobName, docId);
    }

    public void runContinuous(String jobName, String type, String documentId, long intervalMillis, boolean stopOnFailure) {
        String docId = documentId == null ? "ALL" : documentId;
        log.info("🔁 Démarrage orchestration continue dédiée à '{}' (type={} documentId={} stopOnFailure={})", jobName, type, docId, stopOnFailure);
        int cycle = 0;
        while (true) {
            cycle++;
            try {
                runOnce(jobName, type, docId);
            } catch (Exception e) {
                log.error("❌ '{}' échoué au cycle {} pour documentId={}: {}", jobName, cycle, docId, e.getMessage(), e);
                if (stopOnFailure) {
                    log.info("⛔ Arrêt de l'orchestration continue dédiée suite à l'échec (stopOnFailure=true)");
                    throw new IllegalStateException(e);
                } else {
                    log.info("⏸️ Pause 120s avant réessai...");
                    try {
                        Thread.sleep(120_000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Orchestration interrompue", ie);
                    }
                    continue;
                }
            }

            try {
                log.info("⏸️ Pause {}ms avant prochain cycle (cycle #{})", intervalMillis, cycle);
                Thread.sleep(intervalMillis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.info("🛑 Orchestration continue interrompue proprement (interrupt)");
                return;
            }
        }
    }
}
