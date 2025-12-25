package bj.gouv.sgg.orchestrator;

import bj.gouv.sgg.service.GenericOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrateur dédié au job `fetchCurrentJob`.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FetchCurrentOrchestrator {

    private final GenericOrchestrator genericOrchestrator;
    private final java.util.concurrent.atomic.AtomicReference<java.time.LocalDate> lastRunDate = new java.util.concurrent.atomic.AtomicReference<>();

    public void runOnce(String type, String documentId) throws Exception {
        genericOrchestrator.runOnce("fetchCurrentJob", type, documentId);
        // Enregistrer la date d'exécution réussie
        lastRunDate.set(java.time.LocalDate.now());
    }

    public void runContinuous(String type, String documentId, long intervalMillis, boolean stopOnFailure) {
        String docId = documentId == null ? "ALL" : documentId;
        log.info("🔁 Démarrage orchestration continue dédiée à 'fetchCurrentJob' (type={} documentId={} stopOnFailure={})", type, docId, stopOnFailure);

        int cycle = 0;
        while (true) {
            cycle++;
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalDate already = lastRunDate.get();

            if (already != null && already.equals(today)) {
                log.info("⏭️ fetchCurrentJob déjà exécuté aujourd'hui ({}). Skipping cycle #{}.", today, cycle);
            } else {
                try {
                    runOnce(type, documentId);
                } catch (Exception e) {
                    log.error("❌ 'fetchCurrentJob' échoué au cycle {}: {}", cycle, e.getMessage(), e);
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
                    }
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
