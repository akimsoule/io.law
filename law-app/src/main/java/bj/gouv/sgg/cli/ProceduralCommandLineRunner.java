package bj.gouv.sgg.cli;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CommandLineRunner procédural : exécute une liste de jobs séquentiellement
 * en utilisant `Launcher#runJob`.
 *
 * Activation via profile Spring : `procedurale`.
 * Usage minimal : --jobs=fetchCurrentJob,downloadJob --type=loi
 */
@Slf4j
@Component
@Profile("procedural")
@RequiredArgsConstructor
public class ProceduralCommandLineRunner implements ApplicationRunner {

    private final Launcher launcher;
    private LocalDate fetchCurrentLastDateRun = null;

    @Override
    public void run(ApplicationArguments args) {
        // type
        String type = args.containsOption("type") ? args.getOptionValues("type").get(0) : "loi";

        // jobs list (CSV)
        final List<String> jobList;
        if (args.containsOption("jobs")) {
            String jobsCsv = args.getOptionValues("jobs").get(0);
            List<String> tmp = Arrays.stream(jobsCsv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            jobList = tmp.isEmpty() ? Launcher.FULL_PIPELINE : tmp;
        } else {
            jobList = Launcher.FULL_PIPELINE;
        }

        Map<String, String> params = new HashMap<>();
        params.put("type", type);

        // Mode boucle : exécution périodique (logs simples)
        log.info("▶️ Procedural runner démarré (type={}, jobs={})", type, jobList);

        while (true) {
            for (String jobName : jobList) {
                try {
                    // Si c'est le fetcher, on traite sa logique spécifique (max 1x/jour)
                    if ("fetchCurrentJob".equals(jobName)) {
                        if (LocalDate.now().equals(fetchCurrentLastDateRun)) {
                            log.info("⏭️ '{}' ignoré — déjà exécuté aujourd'hui ({})", jobName,
                                    fetchCurrentLastDateRun);
                            continue;
                        }
                        log.info("▶️ Démarrage de '{}'", jobName);
                        long start = System.currentTimeMillis();
                        launcher.runJob(jobName, params);
                        fetchCurrentLastDateRun = LocalDate.now();
                        log.info("✅ '{}' terminé en {} ms", jobName, System.currentTimeMillis() - start);
                    } else {
                        log.info("▶️ Démarrage de '{}'", jobName);
                        long start = System.currentTimeMillis();
                        launcher.runJob(jobName, params);
                        log.info("✅ '{}' terminé en {} ms", jobName, System.currentTimeMillis() - start);
                    }
                } catch (Exception e) {
                    log.error("❌ Erreur lors du job '{}': {}", jobName, e.getMessage());
                }

                // Pause pour éviter de saturer le CPU
                try {
                    long sleepMillis = 30_000L; // 30s
                    log.info("⏸ Pause de {} ms avant de continuer (après le job '{}')", sleepMillis, jobName);
                    Thread.sleep(sleepMillis); // 30s
                    log.info("🔁 Reprise après une pause de {} ms", sleepMillis);
                } catch (InterruptedException e) {
                    log.info("Interruption reçue pendant la pause, arrêt du Procedural runner");
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
