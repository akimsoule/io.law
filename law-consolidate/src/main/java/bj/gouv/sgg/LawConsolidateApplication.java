package bj.gouv.sgg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application Spring Boot pour la consolidation des documents JSON.
 * 
 * Workflow:
 * 1. Lit les documents avec status EXTRACTED
 * 2. Vérifie que le fichier JSON existe
 * 3. Change le status vers CONSOLIDATED
 */
@SpringBootApplication(scanBasePackages = {
        "bj.gouv.sgg.batch",
        "bj.gouv.sgg.service",
        "bj.gouv.sgg.repository",
        "bj.gouv.sgg.config"
})
@ConfigurationPropertiesScan
public class LawConsolidateApplication {
    
    public static void main(String[] args) {
        System.exit(SpringApplication.exit(
                SpringApplication.run(LawConsolidateApplication.class, args)
        ));
    }
}
