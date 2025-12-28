package bj.gouv.sgg.cli;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service d'orchestration des jobs Spring Batch.
 * 
 * <p>
 * Responsabilités :
 * <ul>
 * <li>Résolution des jobs par nom depuis le contexte Spring</li>
 * <li>Construction des JobParameters depuis les arguments CLI</li>
 * <li>Lancement des jobs via JobLauncher</li>
 * <li>Gestion des erreurs d'exécution</li>
 * </ul>
 * 
 * <p>
 * Jobs supportés :
 * <ul>
 * <li>fetchCurrentJob : --type=loi|decret [--{@link #MAX_ITEMS maxItems}=N]
 * [--documentId=ID]</li>
 * <li>fetchPreviousJob : --type=loi|decret [--documentId=ID]</li>
 * <li>downloadJob : --type=loi|decret [--documentId=ID]</li>
 * <li>ocrJob : --type=loi|decret [--documentId=ID]</li>
 * <li>extractJob : --type=loi|decret [--documentId=ID]</li>
 * <li>jsonConversionJob : --type=loi|decret [--documentId=ID]</li>
 * <li>consolidateJob : --type=loi|decret [--documentId=ID]</li>
 * </ul>
 * 
 * @author Law Team
 * @version 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Launcher {

    private final JobLauncher jobLauncher;
    private final ApplicationContext context;

    private static final String DOCUMENT_ID = "documentId";
    private static final String MAX_ITEMS = "maxItems";
    private static final String PIPELINE_BORDER = "========================================";

    /** Ordre et noms des jobs pour le pipeline complet. */
    public static final java.util.List<String> FULL_PIPELINE = java.util.List.of(
            "fetchCurrentJob",
            "fetchPreviousJob",
            "downloadJob",
            "ocrJob",
            "ocrJsonJob",
            "pdfToImagesJob",
            "jsonConversionJob",
            "consolidateJob");

    /**
     * Lance un job Spring Batch avec les paramètres fournis.
     * 
     * @param jobName    Nom du job à exécuter
     * @param parameters Map des paramètres (type, documentId, maxItems, etc.)
     * @throws Exception si le job n'existe pas ou échoue
     */
    public void runJob(String jobName, Map<String, String> parameters) throws Exception {
        log.info("=== LANCEMENT DU JOB : {} ===", jobName);
        log.info("Paramètres : {}", parameters);

        // Résolution du job depuis le contexte Spring
        Job job = context.getBean(jobName, Job.class);

        // Construction des JobParameters via helper centralisé
        JobParameters jobParameters = buildJobParameters(parameters);

        // Lancement du job
        log.info("Démarrage du job '{}' avec type={}", jobName, parameters.getOrDefault("type", "loi"));
        var execution = jobLauncher.run(job, jobParameters);

        // Vérification du statut
        if (execution.getStatus().isUnsuccessful()) {
            log.error("Job '{}' terminé avec statut : {}", jobName, execution.getStatus());
            throw new IllegalStateException("Job failed with status: " + execution.getStatus());
        }

        log.info("Job '{}' terminé avec succès : {}", jobName, execution.getStatus());
    }

    /**
     * Construit les JobParameters à partir d'un map de paramètres simples.
     */
    private JobParameters buildJobParameters(Map<String, String> parameters) {
        JobParametersBuilder builder = new JobParametersBuilder();

        // Paramètres obligatoires
        String type = parameters.getOrDefault("type", "loi");
        builder.addString("type", type);

        // Paramètres optionnels
        if (parameters.containsKey(DOCUMENT_ID)) {
            builder.addString(DOCUMENT_ID, parameters.get(DOCUMENT_ID));
        }

        if (parameters.containsKey(MAX_ITEMS)) {
            builder.addString(MAX_ITEMS, parameters.get(MAX_ITEMS));
        }

        // Timestamp pour unicité (évite les rejets Spring Batch)
        builder.addString("timestamp", LocalDateTime.now().toString());

        return builder.toJobParameters();
    }

    /**
     * Lance un pipeline complet de jobs en respectant l'ordre défini par
     * {@link #FULL_PIPELINE}.
     *
     * @param type       Type de document (loi ou decret)
     * @param documentId ID spécifique ou null pour tous
     * @throws Exception si un des jobs échoue
     */
    public void runFullPipeline(String type, String documentId) throws Exception {
        log.info(PIPELINE_BORDER);
        log.info("  PIPELINE COMPLET - type={} " + DOCUMENT_ID + "={}", type, documentId);
        log.info(PIPELINE_BORDER);

        Map<String, String> params = Map.of(
                "type", type,
                DOCUMENT_ID, documentId != null ? documentId : "ALL");

        int total = FULL_PIPELINE.size();
        for (int i = 0; i < total; i++) {
            String jobName = FULL_PIPELINE.get(i);
            log.info("\n[{}/{}] ▶️ Exécution du job : {}", i + 1, total, jobName);
            runJob(jobName, params);
        }

        log.info("\n" + PIPELINE_BORDER);
        log.info("  PIPELINE COMPLET TERMINÉ AVEC SUCCÈS");
        log.info(PIPELINE_BORDER);
    }
}
