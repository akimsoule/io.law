package bj.gouv.sgg.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Application Spring Boot pour l'extraction JSON depuis texte OCR.
 * 
 * <p>Workflow :
 * <ul>
 *   <li>INPUT: Documents avec status OCRED_V2</li>
 *   <li>PROCESSING: Extraction articles via regex patterns</li>
 *   <li>OUTPUT: Fichier JSON + status JSON_EXTRACTED</li>
 * </ul>
 * 
 * <p>Architecture Spring Batch :
 * <pre>
 * OcrJsonReader (DB) → OcrJsonProcessor (Parser) → OcrJsonWriter (DB + File)
 * </pre>
 */
@SpringBootApplication(scanBasePackages = {
        "bj.gouv.sgg.batch",      // batch components
        "bj.gouv.sgg.service",     // services
        "bj.gouv.sgg.repository",  // repositories
        "bj.gouv.sgg.config"       // configurations
})
@EntityScan(basePackages = "bj.gouv.sgg.entity")
@EnableJpaRepositories(basePackages = "bj.gouv.sgg.repository")
@ConfigurationPropertiesScan
public class LawOcrJsonApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(
                SpringApplication.run(LawOcrJsonApplication.class, args)
        ));
    }
}
