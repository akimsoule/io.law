package bj.gouv.sgg.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Runner pour démarrer l'orchestration continue via CLI.
 * 
 * Usage:
 *   java -jar law-app.jar --job=orchestrate
 *   java -jar law-app.jar --job=orchestrate --skip-fetch-daily=false
 * 
 * Options:
 *   --skip-fetch-daily=true  : Skip fetchCurrentJob si déjà exécuté aujourd'hui (défaut)
 *   --skip-fetch-daily=false : Exécuter fetchCurrentJob à chaque cycle
 * 
 * Arrêt:
 *   Ctrl+C (SIGINT)
 */
@Slf4j
@Component
@Order(1) // Avant JobCommandLineRunner
@RequiredArgsConstructor
public class OrchestratorRunner implements CommandLineRunner {

    private static final String ORCHESTRATE_ARG = "--job=orchestrate";
    private static final String SKIP_FETCH_DAILY_PREFIX = "--skip-fetch-daily=";
    private static final String TYPE_PREFIX = "--type=";
    
    private final PipelineOrchestrator orchestrator;

    @Override
    public void run(String... args) throws Exception {
        boolean shouldOrchestrate = Arrays.stream(args)
            .anyMatch(arg -> arg.equals(ORCHESTRATE_ARG));

        if (shouldOrchestrate) {
            log.info("🎯 Mode orchestration continue activé");
            
            // Parser l'option skip-fetch-daily
            boolean skipFetchDaily = parseSkipFetchDaily(args);
            orchestrator.setSkipFetchCurrentIfToday(skipFetchDaily);

            // Parser le filtre global de type (optionnel)
            parseAndApplyTypeFilter(args);
            
            // Bloque jusqu'à arrêt manuel
            orchestrator.startContinuousOrchestration();
            
            // Si on arrive ici, c'est que l'orchestration s'est arrêtée
            log.info("👋 Arrêt de l'application");
            System.exit(0);
        }
    }
    
    /**
     * Parse l'argument --skip-fetch-daily=true|false
     * 
     * @param args Arguments CLI
     * @return true si skip (défaut), false si forcer à chaque cycle
     */
    private boolean parseSkipFetchDaily(String[] args) {
        return Arrays.stream(args)
            .filter(arg -> arg.startsWith(SKIP_FETCH_DAILY_PREFIX))
            .map(arg -> arg.substring(SKIP_FETCH_DAILY_PREFIX.length()))
            .map(value -> {
                boolean result = !value.equalsIgnoreCase("false");
                log.info("⚙️  Option détectée: skip-fetch-daily={}", result);
                return result;
            })
            .findFirst()
            .orElse(true); // Défaut: true (skip si déjà exécuté aujourd'hui)
    }

    /**
     * Parse l'argument --type=loi|decret et applique au PipelineOrchestrator
     */
    private void parseAndApplyTypeFilter(String[] args) {
        Arrays.stream(args)
            .filter(arg -> arg.startsWith(TYPE_PREFIX))
            .map(arg -> arg.substring(TYPE_PREFIX.length()))
            .findFirst()
            .ifPresent(type -> {
                orchestrator.setTypeFilter(type);
                log.info("⚙️  Option détectée: type={}", type);
            });
    }
}
