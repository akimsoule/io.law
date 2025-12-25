package bj.gouv.sgg.orchestrator;

import bj.gouv.sgg.orchestrator.main.JobOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/**
 * Runner CLI pour parser les arguments et lancer les jobs.
 * 
 * <p>Exemples d'utilisation :
 * <pre>
 * # Job individuel
 * java -jar law-app.jar --job=fetchCurrentJob --type=loi --maxItems=5
 * java -jar law-app.jar --job=downloadJob --type=decret --documentId=decret-2023-100
 * java -jar law-app.jar --job=ocrJob --type=loi
 * java -jar law-app.jar --job=extractJob --type=loi
 * java -jar law-app.jar --job=jsonConversionJob --type=decret
 * 
 * # Pipeline complet
 * java -jar law-app.jar --pipeline=fullPipeline --type=loi
 * java -jar law-app.jar --pipeline=fullPipeline --type=loi --documentId=loi-2025-001
 * </pre>
 * 
 * @author Law Team
 * @version 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandLineJobRunner implements ApplicationRunner {

    private static final String SEPARATOR_LINE = "=================================================";
    private static final String DOCUMENT_ID = "documentId";
    private static final String MAX_ITEMS = "maxItems";

    private final JobOrchestrator orchestrator;
    private final OrchestrationService orchestrationService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info(SEPARATOR_LINE);
        log.info("  LAW ORCHESTRATOR - COMMAND LINE RUNNER");
        log.info(SEPARATOR_LINE);
        
        // Vérification des arguments
        if (args.getOptionNames().isEmpty()) {
            printUsage();
            return;
        }
        
        // Mode orchestration continue
        if (args.containsOption("job") && "orchestrate".equals(args.getOptionValues("job").get(0))) {
            String type = args.getOptionValues("type") != null 
                ? args.getOptionValues("type").get(0) 
                : "loi";
            boolean skipFetchDaily = Boolean.parseBoolean(
                args.getOptionValues("skip-fetch-daily") != null
                    ? args.getOptionValues("skip-fetch-daily").get(0)
                    : "true");
            
            log.info("Mode ORCHESTRATION CONTINUE");
            log.info("Type : {}", type);
            log.info("Skip fetch daily : {}", skipFetchDaily);
            
            orchestrationService.runContinuousOrchestration(type, skipFetchDaily);
            return; // Ne devrait jamais arriver (boucle infinie)
        }
        
        // Mode pipeline
        if (args.containsOption("pipeline")) {
            String pipeline = args.getOptionValues("pipeline").get(0);
            String type = args.getOptionValues("type") != null 
                ? args.getOptionValues("type").get(0) 
                : "loi";
            String documentId = args.getOptionValues(DOCUMENT_ID) != null 
                ? args.getOptionValues(DOCUMENT_ID).get(0) 
                : null;
            
            log.info("Mode PIPELINE : {}", pipeline);
            log.info("Type : {}", type);
            log.info("DocumentId : {}", documentId);
            
            if ("fullPipeline".equals(pipeline)) {
                orchestrator.runFullPipeline(type, documentId);
                log.info("Pipeline terminé. Fermeture de l'application.");
                System.exit(0);
            } else {
                log.error("Pipeline inconnu : {}", pipeline);
                printUsage();
                System.exit(1);
            }
        }
        
        // Mode job individuel
        if (args.containsOption("job")) {
            String jobName = args.getOptionValues("job").get(0);
            Map<String, String> parameters = new HashMap<>();
            
            // Type (obligatoire sauf pour certains jobs)
            if (args.containsOption("type")) {
                parameters.put("type", args.getOptionValues("type").get(0));
            } else {
                parameters.put("type", "loi"); // Valeur par défaut
            }
            
            // DocumentId (optionnel)
            if (args.containsOption(DOCUMENT_ID)) {
                parameters.put(DOCUMENT_ID, args.getOptionValues(DOCUMENT_ID).get(0));
            }
            
            // MaxItems (optionnel)
            if (args.containsOption(MAX_ITEMS)) {
                parameters.put(MAX_ITEMS, args.getOptionValues(MAX_ITEMS).get(0));
            }
            
            log.info("Mode JOB INDIVIDUEL : {}", jobName);
            log.info("Paramètres : {}", parameters);
            
            orchestrator.runJob(jobName, parameters);
            log.info("Job terminé. Fermeture de l'application.");
            System.exit(0);
        }
        
        // Aucun mode reconnu
        log.error("Aucun mode d'exécution spécifié (--job ou --pipeline)");
        printUsage();
        System.exit(1);
    }

    /**
     * Affiche l'aide d'utilisation.
     */
    private void printUsage() {
        log.info("\n{}", SEPARATOR_LINE);
        log.info("  USAGE");
        log.info(SEPARATOR_LINE);
        log.info("\nMODE ORCHESTRATION CONTINUE :");
        log.info("  java -jar law-app.jar --job=orchestrate --type=<type> [--skip-fetch-daily=true|false]");
        log.info("\nOptions :");
        log.info("  --skip-fetch-daily=true    : Skip fetchCurrent si déjà exécuté aujourd'hui (défaut)");
        log.info("  --skip-fetch-daily=false   : Forcer fetchCurrent à chaque cycle");
        log.info("\nMODE JOB INDIVIDUEL :");
        log.info("  java -jar law-app.jar --job=<jobName> --type=<type> [options]");
        log.info("\nJobs disponibles :");
        log.info("  - orchestrate        : Orchestration continue (fetchCurrent 1x/jour)");
        log.info("  - fetchCurrentJob    : Récupération documents courants");
        log.info("  - fetchPreviousJob   : Récupération documents antérieurs");
        log.info("  - downloadJob        : Téléchargement PDFs");
        log.info("  - ocrJob             : Extraction OCR des PDFs (law-pdf-ocr)");
        log.info("  - ocrJsonJob         : Extraction JSON depuis OCR (law-ocr-json)");
        log.info("  - pdfToImagesJob     : Conversion PDF → Images (law-pdf-img)");
        log.info("  - jsonConversionJob  : Pipeline complet PDF → OCR → JSON");
        log.info("  - consolidateJob     : Consolidation finale");
        log.info("\nOptions :");
        log.info("  --type=<loi|decret>        : Type de document (défaut: loi)");
        log.info("  --documentId=<id>          : ID spécifique (optionnel)");
        log.info("  --" + MAX_ITEMS + "=<n>         : Limite de documents (optionnel)");
        log.info("\nMODE PIPELINE COMPLET :");
        log.info("  java -jar law-app.jar --pipeline=fullPipeline --type=<type> [--documentId=<id>]");
        log.info("\nPipeline fullPipeline exécute dans l'ordre :");
        log.info("  1. fetchCurrentJob - Année courante");
        log.info("  2. fetchPreviousJob - Années précédentes");
        log.info("  3. downloadJob - Téléchargement PDFs");
        log.info("  4. pdfToImagesJob - Conversion PDF → Images");
        log.info("  5. jsonConversionJob - Extraction OCR + JSON");
        log.info("  6. consolidateJob - Consolidation finale");
        log.info("\nExemples :");
        log.info("  java -jar law-app.jar --job=fetchCurrentJob --type=loi --" + MAX_ITEMS + "=5");
        log.info("  java -jar law-app.jar --job=fetchPreviousJob --type=decret --documentId=decret-2023-100");
        log.info("  java -jar law-app.jar --job=downloadJob --type=decret --documentId=decret-2023-100");
        log.info("  java -jar law-app.jar --job=ocrJob --type=loi");
        log.info("  java -jar law-app.jar --job=ocrJsonJob --type=loi");
        log.info("  java -jar law-app.jar --pipeline=fullPipeline --type=loi");
        log.info("{}\n", SEPARATOR_LINE);
    }
}
