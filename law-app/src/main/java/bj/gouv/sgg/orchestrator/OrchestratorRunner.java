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
    
    private final PipelineOrchestrator orchestrator;

    @Override
    public void run(String... args) throws Exception {
        boolean shouldOrchestrate = Arrays.stream(args)
            .anyMatch(arg -> arg.equals(ORCHESTRATE_ARG));

        if (shouldOrchestrate) {
            log.info("🎯 Mode orchestration continue activé");
            
            // Bloque jusqu'à arrêt manuel
            orchestrator.startContinuousOrchestration();
            
            // Si on arrive ici, c'est que l'orchestration s'est arrêtée
            log.info("👋 Arrêt de l'application");
            System.exit(0);
        }
    }
}
