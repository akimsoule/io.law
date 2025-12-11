package bj.gouv.sgg.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrateur du pipeline complet de traitement.
 * 
 * Exécute de manière continue et cyclique :
 * 1. fetchCurrentJob → Détecte nouveaux documents année courante
 * 2. fetchPreviousJob → Scan années précédentes (1960 à année-1)
 * 3. downloadJob → Télécharge PDFs
 * 4. pdfToJsonJob → Extraction OCR/IA
 * 5. consolidateJob → Import en base de données
 * 6. fixJob → Correction et amélioration continue
 * 
 * Cycle indéfini jusqu'à arrêt manuel (Ctrl+C)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PipelineOrchestrator {

    private final JobLauncher jobLauncher;
    private final Job fetchCurrentJob;
    private final Job fetchPreviousJob;
    private final Job downloadJob;
    private final Job pdfToJsonJob;
    private final Job consolidateJob;
    private final Job fixJob;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger cycleCount = new AtomicInteger(0);
    private String lastFetchCurrentDate = null; // Dernière exécution de fetchCurrentJob (format: yyyy-MM-dd)

    private static final long CYCLE_DELAY_MS = 60_000; // 1 minute entre cycles
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Démarre l'orchestration continue.
     * Bloque jusqu'à arrêt manuel (Ctrl+C)
     */
    public void startContinuousOrchestration() {
        if (running.compareAndSet(false, true)) {
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("🚀 DÉMARRAGE ORCHESTRATION CONTINUE");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("📋 Pipeline: fetchCurrent → fetchPrevious → download → extract → consolidate → fix");
            log.info("🔄 Mode: Continu (arrêt: Ctrl+C)");
            log.info("⏱️  Délai entre cycles: {}ms", CYCLE_DELAY_MS);
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // Hook pour arrêt propre
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("⏹️  Signal d'arrêt reçu (Ctrl+C)");
                stopOrchestration();
            }));

            try {
                while (running.get()) {
                    executeCycle();
                    
                    if (running.get()) {
                        log.info("⏸️  Pause {} secondes avant prochain cycle...", CYCLE_DELAY_MS / 1000);
                        Thread.sleep(CYCLE_DELAY_MS);
                    }
                }
            } catch (InterruptedException e) {
                log.warn("⚠️ Orchestration interrompue");
                Thread.currentThread().interrupt();
            } finally {
                log.info("🏁 Orchestration terminée - {} cycles exécutés", cycleCount.get());
            }
        } else {
            log.warn("⚠️ Orchestration déjà en cours");
        }
    }

    /**
     * Arrête l'orchestration
     */
    public void stopOrchestration() {
        if (running.compareAndSet(true, false)) {
            log.info("⏹️  Arrêt de l'orchestration demandé");
        }
    }

    /**
     * Exécute un cycle complet du pipeline
     */
    private void executeCycle() {
        int cycle = cycleCount.incrementAndGet();
        String timestamp = LocalDateTime.now().format(FORMATTER);
        
        log.info("");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("🔄 CYCLE #{} - {}", cycle, timestamp);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        boolean success = true;

        // 1. Fetch année courante (1 fois par jour)
        String today = LocalDateTime.now().format(DATE_FORMATTER);
        if (!today.equals(lastFetchCurrentDate)) {
            success &= executeJob(fetchCurrentJob, "1/6 📡 Fetch année courante (quotidien)");
            if (success) {
                lastFetchCurrentDate = today;
                log.info("📅 Prochaine exécution de fetchCurrentJob: demain");
            }
        } else {
            log.info("⏭️  1/6 Fetch année courante déjà exécuté aujourd'hui, skip");
        }

        // 2. Fetch années précédentes (chaque cycle)
        if (success && running.get()) {
            success &= executeJob(fetchPreviousJob, "2/6 📅 Fetch années précédentes");
        }

        // 3. Download PDFs
        if (success && running.get()) {
            success &= executeJob(downloadJob, "3/6 📥 Download PDFs");
        }

        // 4. Extraction PDF → JSON
        if (success && running.get()) {
            success &= executeJob(pdfToJsonJob, "4/6 📄 Extraction JSON");
        }

        // 5. Consolidation en base
        if (success && running.get()) {
            success &= executeJob(consolidateJob, "5/6 💾 Consolidation BD");
        }

        // 6. Correction automatique
        if (running.get()) {
            executeJob(fixJob, "6/6 🔧 Correction & amélioration");
        }

        if (success) {
            log.info("✅ Cycle #{} terminé avec succès", cycle);
        } else {
            log.warn("⚠️ Cycle #{} terminé avec erreurs (voir logs ci-dessus)", cycle);
        }
        
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * Exécute un job avec gestion d'erreur
     * 
     * @return true si succès, false si échec
     */
    private boolean executeJob(Job job, String stepLabel) {
        String jobName = job.getName();
        log.info("");
        log.info("▶️  {} - {}", stepLabel, jobName);
        log.info("─────────────────────────────────────────────────────────────");

        try {
            JobParameters params = new JobParametersBuilder()
                .addString("timestamp", LocalDateTime.now().toString())
                .addLong("cycle", (long) cycleCount.get())
                .toJobParameters();

            JobExecution execution = jobLauncher.run(job, params);
            BatchStatus status = execution.getStatus();

            if (status.isUnsuccessful()) {
                log.error("❌ {} échoué: {}", jobName, status);
                return false;
            }

            log.info("✅ {} terminé: {}", jobName, status);
            return true;

        } catch (JobExecutionAlreadyRunningException e) {
            log.warn("⚠️ {} déjà en cours d'exécution, skip", jobName);
            return true; // Ne bloque pas le pipeline
        } catch (JobRestartException | JobInstanceAlreadyCompleteException e) {
            log.warn("⚠️ {} : {}", jobName, e.getMessage());
            return true; // Ne bloque pas le pipeline
        } catch (Exception e) {
            log.error("❌ Erreur lors de l'exécution de {}", jobName, e);
            return false;
        }
    }

    /**
     * Vérifie si l'orchestration est en cours
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Retourne le nombre de cycles exécutés
     */
    public int getCycleCount() {
        return cycleCount.get();
    }
}
