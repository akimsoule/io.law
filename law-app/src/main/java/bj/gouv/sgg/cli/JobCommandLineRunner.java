package bj.gouv.sgg.cli;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * Runner CLI pour exécuter les jobs Spring Batch via ligne de commande.
 * 
 * Usage:
 *   java -jar law-api.jar --job=fetchCurrentJob
 *   java -jar law-api.jar --job=fetchCurrentJob --doc=loi-2025-17
 *   java -jar law-api.jar --job=fetchCurrentJob --force
 *   java -jar law-api.jar --job=fetchCurrentJob --doc=loi-2025-17 --force
 *   java -jar law-api.jar --job=downloadJob --year=2024
 *   java -jar law-api.jar --job=pdfToJsonJob
 *   java -jar law-api.jar --job=pdfToJsonJob --doc=loi-2024-15
 *   java -jar law-api.jar --job=pdfToJsonJob --force --maxDocuments=20
 *   java -jar law-api.jar --spring.main.web-application-type=none --job=ocrJob
 * 
 * Arguments supportés:
 *   --job=<jobName>          : Nom du job à exécuter
 *   --doc=<documentId>       : Traite un seul document spécifique (ex: loi-2025-17)
 *   --force                  : Force le re-traitement du document spécifié (--doc requis)
 *   --maxDocuments=<number>  : Nombre max de documents à traiter (défaut: 10 pour pdfToJsonJob)
 *   --params=<key>=<value>   : Paramètres additionnels (peut être répété)
 * 
 * Jobs supportés avec paramètres:
 *   - fetchCurrentJob       : --doc, --force
 *   - fetchPreviousJob      : --force
 *   - downloadJob           : --doc, --force, --maxDocuments
 *   - pdfToJsonJob          : --doc, --doc + --force, --maxDocuments (défaut: 10)
 *   - ocrJob                : (tous documents DOWNLOADED)
 *   - consolidationJob      : (tous documents EXTRACTED)
 * 
 * Note: --force nécessite --doc pour pdfToJsonJob (force + document spécifique)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobCommandLineRunner implements CommandLineRunner {

    private static final String JOB_ARG_PREFIX = "--job=";
    private static final String PARAMS_ARG_PREFIX = "--params=";
    private static final String SPRING_ARG_PREFIX = "--spring.";
    private static final String ARG_PREFIX = "--";

    private final JobLauncher jobLauncher;
    private final ApplicationContext applicationContext;

    @Override
    public void run(String... args) throws Exception {
        String jobName = extractJobName(args);
        
        if (jobName == null) {
            log.debug("No --job argument provided, skipping CLI job execution");
            return;
        }

        log.info("🚀 CLI execution requested for job: {}", jobName);

        try {
            Job job = applicationContext.getBean(jobName, Job.class);
            JobParameters jobParameters = buildJobParameters(args);
            
            log.info("▶️  Starting job: {} with parameters: {}", jobName, jobParameters);
            var execution = jobLauncher.run(job, jobParameters);
            
            log.info("✅ Job {} completed with status: {}", jobName, execution.getStatus());
            
            // Exit avec code approprié
            if (execution.getStatus().isUnsuccessful()) {
                log.error("❌ Job failed, exiting with code 1");
                System.exit(1);
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to execute job: {}", jobName, e);
            System.exit(1);
        }
    }

    /**
     * Extrait le nom du job depuis les arguments CLI
     */
    private String extractJobName(String[] args) {
        return Arrays.stream(args)
            .filter(arg -> arg.startsWith(JOB_ARG_PREFIX))
            .map(arg -> arg.substring(JOB_ARG_PREFIX.length()))
            .findFirst()
            .orElse(null);
    }

    /**
     * Construit les JobParameters depuis les arguments CLI
     */
    private JobParameters buildJobParameters(String[] args) {
        JobParametersBuilder builder = new JobParametersBuilder();
        
        // Timestamp unique pour permettre les réexécutions
        builder.addString("timestamp", LocalDateTime.now().toString());
        
        // Extraction paramètres custom (--params=key=value)
        Arrays.stream(args)
            .filter(arg -> arg.startsWith(PARAMS_ARG_PREFIX))
            .map(arg -> arg.substring(PARAMS_ARG_PREFIX.length()))
            .forEach(param -> {
                String[] parts = param.split("=", 2);
                if (parts.length == 2) {
                    builder.addString(parts[0], parts[1]);
                    log.debug("Added parameter: {}={}", parts[0], parts[1]);
                }
            });
        
        // Support alias : --doc=xxx devient aussi --documentId=xxx
        String docParam = null;
        for (String arg : args) {
            if (arg.startsWith("--doc=")) {
                docParam = arg.substring("--doc=".length());
                break;
            }
        }
        if (docParam != null) {
            builder.addString("documentId", docParam);
            log.debug("Mapped --doc={} to documentId={}", docParam, docParam);
        }
        
        // Support direct des paramètres simples (--year=2024, --doc=loi-2025-17)
        Arrays.stream(args)
            .filter(arg -> arg.startsWith(ARG_PREFIX) && !arg.startsWith(JOB_ARG_PREFIX) && !arg.startsWith(PARAMS_ARG_PREFIX) && !arg.startsWith(SPRING_ARG_PREFIX))
            .forEach(arg -> {
                String[] parts = arg.substring(ARG_PREFIX.length()).split("=", 2);
                if (parts.length == 2) {
                    builder.addString(parts[0], parts[1]);
                    log.debug("Added parameter: {}={}", parts[0], parts[1]);
                } else if (parts.length == 1) {
                    // Support des flags booléens (--force)
                    builder.addString(parts[0], "true");
                    log.debug("Added flag: {}=true", parts[0]);
                }
            });
        
        return builder.toJobParameters();
    }
}
