package bj.gouv.sgg.orchestrator;

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
 * <p>Responsabilités :
 * <ul>
 *   <li>Résolution des jobs par nom depuis le contexte Spring</li>
 *   <li>Construction des JobParameters depuis les arguments CLI</li>
 *   <li>Lancement des jobs via JobLauncher</li>
 *   <li>Gestion des erreurs d'exécution</li>
 * </ul>
 * 
 * <p>Jobs supportés :
 * <ul>
 *   <li>fetchCurrentJob : --type=loi|decret [--{@link #MAX_DOCUMENTS maxDocuments}=N] [--documentId=ID]</li>
 *   <li>fetchPreviousJob : --type=loi|decret [--documentId=ID]</li>
 *   <li>downloadJob : --type=loi|decret [--documentId=ID]</li>
 *   <li>ocrJob : --type=loi|decret [--documentId=ID]</li>
 *   <li>extractJob : --type=loi|decret [--documentId=ID]</li>
 *   <li>jsonConversionJob : --type=loi|decret [--documentId=ID]</li>
 *   <li>consolidateJob : --type=loi|decret [--documentId=ID]</li>
 * </ul>
 * 
 * @author Law Team
 * @version 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobOrchestrator {

    private final JobLauncher jobLauncher;
    private final ApplicationContext context;

    private static final String DOCUMENT_ID = "documentId";
    private static final String MAX_DOCUMENTS = "maxDocuments";
    private static final String PIPELINE_BORDER = "========================================";

    /**
     * Lance un job Spring Batch avec les paramètres fournis.
     * 
     * @param jobName Nom du job à exécuter
     * @param parameters Map des paramètres (type, documentId, maxDocuments, etc.)
     * @throws Exception si le job n'existe pas ou échoue
     */
    public void runJob(String jobName, Map<String, String> parameters) throws Exception {
        log.info("=== LANCEMENT DU JOB : {} ===", jobName);
        log.info("Paramètres : {}", parameters);
        
        // Résolution du job depuis le contexte Spring
        Job job = context.getBean(jobName, Job.class);
        
        // Construction des JobParameters
        JobParametersBuilder builder = new JobParametersBuilder();
        
        // Paramètres obligatoires
        String type = parameters.getOrDefault("type", "loi");
        builder.addString("type", type);
        
        // Paramètres optionnels
        if (parameters.containsKey(DOCUMENT_ID)) {
            builder.addString(DOCUMENT_ID, parameters.get(DOCUMENT_ID));
        }
        
        if (parameters.containsKey(MAX_DOCUMENTS)) {
            builder.addString(MAX_DOCUMENTS, parameters.get(MAX_DOCUMENTS));
        }
        
        // Timestamp pour unicité (évite les rejets Spring Batch)
        builder.addString("timestamp", LocalDateTime.now().toString());
        
        JobParameters jobParameters = builder.toJobParameters();
        
        // Lancement du job
        log.info("Démarrage du job '{}' avec type={}", jobName, type);
        var execution = jobLauncher.run(job, jobParameters);
        
        // Vérification du statut
        if (execution.getStatus().isUnsuccessful()) {
            log.error("Job '{}' terminé avec statut : {}", jobName, execution.getStatus());
            throw new RuntimeException("Job failed with status: " + execution.getStatus());
        }
        
        log.info("Job '{}' terminé avec succès : {}", jobName, execution.getStatus());
    }

    /**
     * Lance un pipeline complet de jobs.
     * 
     * @param type Type de document (loi ou decret)
     * @param documentId ID spécifique ou null pour tous
     * @throws Exception si un des jobs échoue
     */
    public void runFullPipeline(String type, String documentId) throws Exception {
        log.info(PIPELINE_BORDER);
        log.info("  PIPELINE COMPLET - type={} " + DOCUMENT_ID + "={}", type, documentId);
        log.info(PIPELINE_BORDER);
        
        Map<String, String> params = Map.of(
            "type", type,
            DOCUMENT_ID, documentId != null ? documentId : "ALL"
        );
        
        // 1. Fetch Current Year
        log.info("\n[1/6] FETCH CURRENT - Récupération métadonnées année courante");
        runJob("fetchCurrentJob", params);
        
        // 2. Fetch Previous Years
        log.info("\n[2/6] FETCH PREVIOUS - Récupération métadonnées années précédentes");
        runJob("fetchPreviousJob", params);
        
        // 3. Download
        log.info("\n[3/6] DOWNLOAD - Téléchargement PDFs");
        runJob("downloadJob", params);
        
        // 4. JSON Conversion (PDF → OCR → JSON)
        log.info("\n[4/6] JSON CONVERSION - Extraction complète");
        runJob("jsonConversionJob", params);
        
        // 5. Consolidate
        log.info("\n[5/6] CONSOLIDATE - Consolidation finale");
        runJob("consolidateJob", params);
        
        log.info("\n" + PIPELINE_BORDER);
        log.info("  PIPELINE COMPLET TERMINÉ AVEC SUCCÈS");
        log.info(PIPELINE_BORDER);
    }
}
