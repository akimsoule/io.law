package bj.gouv.sgg.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.NoSuchJobException;
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
    private final JobRegistry jobRegistry;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger cycleCount = new AtomicInteger(0);
    private String lastFetchCurrentDate = null; // Dernière exécution de fetchCurrentJob (format: yyyy-MM-dd)
    private boolean skipFetchCurrentIfToday = true; // Skip fetchCurrentJob si déjà exécuté aujourd'hui
    private String typeFilter = null; // Filtre global de type (ex: "loi"), null = tous

    private static final long CYCLE_DELAY_MS = 5_000; // 5 secondes entre cycles
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String SEPARATOR = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

    /**
     * Configure le skip automatique de fetchCurrentJob.
     * 
     * @param skip true = skip si déjà exécuté aujourd'hui (défaut), false = exécuter à chaque cycle
     */
    public void setSkipFetchCurrentIfToday(boolean skip) {
        this.skipFetchCurrentIfToday = skip;
        log.info("⚙️  Skip fetchCurrentJob si déjà exécuté aujourd'hui: {}", skip);
    }

    /**
     * Configure un filtre global de type document (ex: "loi" ou "decret").
     * Si défini, il est passé à tous les jobs (ignoré par ceux qui ne l'utilisent pas).
     *
     * @param type null pour aucun filtre, sinon valeur normalisée en minuscule
     */
    public void setTypeFilter(String type) {
        if (type != null && !type.isBlank()) {
            this.typeFilter = type.trim().toLowerCase();
            log.info("🎯 Filtre de type activé pour l'orchestration: {}", this.typeFilter);
        } else {
            this.typeFilter = null;
            log.info("🎯 Filtre de type désactivé (tous types)");
        }
    }

    /**
     * Démarre l'orchestration continue.
     * Bloque jusqu'à arrêt manuel (Ctrl+C)
     */
    public void startContinuousOrchestration() {
        if (running.compareAndSet(false, true)) {
            log.info(SEPARATOR);
            log.info("🚀 DÉMARRAGE ORCHESTRATION CONTINUE");
            log.info(SEPARATOR);
            log.info("📋 Pipeline: fetchCurrent → fetchPrevious → download → extract → consolidate → fix");
            log.info("🔄 Mode: Continu (arrêt: Ctrl+C)");
            log.info("⏱️  Délai entre cycles: {}ms", CYCLE_DELAY_MS);
            log.info(SEPARATOR);

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
        log.info(SEPARATOR);
        if (typeFilter != null) {
            log.info("🔄 CYCLE #{} - {} (focus: {})", cycle, timestamp, typeFilter);
        } else {
            log.info("🔄 CYCLE #{} - {}", cycle, timestamp);
        }
        log.info(SEPARATOR);

        // ⚠️ RÉSILIENCE: Aucune erreur ne doit bloquer le pipeline
        // Tous les jobs s'exécutent indépendamment, même en cas d'échec des précédents
        int successCount = 0;
        int failedCount = 0;

        // 1. Fetch année courante (1 fois par jour ou à chaque cycle selon config)
        String today = LocalDateTime.now().format(DATE_FORMATTER);
        boolean shouldSkip = skipFetchCurrentIfToday && today.equals(lastFetchCurrentDate);
        
        if (!shouldSkip) {
            boolean success = executeJob("fetchCurrentJob", "1/6 📡 Fetch année courante" + 
                (skipFetchCurrentIfToday ? " (quotidien)" : " (chaque cycle)"));
            if (success) {
                lastFetchCurrentDate = today;
                successCount++;
                if (skipFetchCurrentIfToday) {
                    log.info("📅 Prochaine exécution de fetchCurrentJob: demain");
                }
            } else {
                failedCount++;
            }
        } else {
            log.info("⏭️  1/6 Fetch année courante déjà exécuté aujourd'hui, skip");
        }

        // 2. Fetch années précédentes (chaque cycle) - TOUJOURS exécuter même si #1 échoue
        if (running.get()) {
            if (executeJob("fetchPreviousJob", "2/6 📅 Fetch années précédentes")) {
                successCount++;
            } else {
                failedCount++;
            }
        }

        // 3. Download PDFs - TOUJOURS exécuter même si fetch échoue
        if (running.get()) {
            if (executeJob("downloadJob", "3/6 📥 Download PDFs")) {
                successCount++;
            } else {
                failedCount++;
            }
        }

        // 4. Extraction PDF → JSON - TOUJOURS exécuter
        if (running.get()) {
            if (executeJob("pdfToJsonJob", "4/6 📄 Extraction JSON")) {
                successCount++;
            } else {
                failedCount++;
            }
        }

        // 5. Consolidation en base - TOUJOURS exécuter
        if (running.get()) {
            if (executeJob("consolidateJob", "5/6 💾 Consolidation BD")) {
                successCount++;
            } else {
                failedCount++;
            }
        }

        // 6. Correction automatique - TOUJOURS exécuter (détecte et corrige les incohérences)
        if (running.get()) {
            if (executeJob("fixJob", "6/6 🔧 Correction & amélioration")) {
                successCount++;
            } else {
                failedCount++;
            }
        }

        // Résumé du cycle
        if (failedCount == 0) {
            log.info("✅ Cycle #{} terminé avec succès - {} jobs exécutés", cycle, successCount);
        } else if (successCount > 0) {
            log.warn("⚠️ Cycle #{} terminé avec {} succès et {} échecs", cycle, successCount, failedCount);
        } else {
            log.error("❌ Cycle #{} terminé - {} jobs échoués (pipeline continue)", cycle, failedCount);
        }
        
        log.info(SEPARATOR);
    }

    /**
     * Exécute un job avec gestion d'erreur
     * 
     * @param jobName Nom du job à exécuter
     * @param stepLabel Label affiché dans les logs
     * @return true si succès, false si échec
     */
    private boolean executeJob(String jobName, String stepLabel) {
        log.info("");
        log.info("▶️  {} - {}", stepLabel, jobName);
        log.info("─────────────────────────────────────────────────────────────");

        try {
            // Récupérer une nouvelle instance du job depuis le registry
            Job job = jobRegistry.getJob(jobName);
            
            JobParametersBuilder paramsBuilder = new JobParametersBuilder()
                .addString("timestamp", LocalDateTime.now().toString())
                .addLong("cycle", (long) cycleCount.get());

            // Propager le filtre de type si défini
            if (typeFilter != null) {
                paramsBuilder.addString("type", typeFilter);
                log.info("🎯 Paramètre type propagé au job {}: {}", jobName, typeFilter);
            }

            JobParameters params = paramsBuilder.toJobParameters();

            JobExecution execution = jobLauncher.run(job, params);
            BatchStatus status = execution.getStatus();

            if (status.isUnsuccessful()) {
                log.error("❌ {} échoué: {}", jobName, status);
                return false;
            }

            log.info("✅ {} terminé: {}", jobName, status);
            return true;

        } catch (NoSuchJobException e) {
            log.error("❌ Job {} introuvable dans le registry - SKIP et CONTINUE", jobName);
            return false; // Job échoué mais pipeline continue
        } catch (JobExecutionAlreadyRunningException e) {
            log.warn("⚠️ {} déjà en cours d'exécution - SKIP et CONTINUE", jobName);
            return true; // Ne bloque pas le pipeline
        } catch (JobRestartException | JobInstanceAlreadyCompleteException e) {
            log.warn("⚠️ {} : {} - SKIP et CONTINUE", jobName, e.getMessage());
            return true; // Ne bloque pas le pipeline
        } catch (Exception e) {
            log.error("❌ Erreur lors de l'exécution de {} - SKIP et CONTINUE pipeline", jobName, e);
            return false; // Job échoué mais pipeline continue
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
