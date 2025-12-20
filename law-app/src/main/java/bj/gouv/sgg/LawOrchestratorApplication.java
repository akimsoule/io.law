package bj.gouv.sgg;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Application principale orchestrant tous les jobs Spring Batch.
 * 
 * <p>Architecture :
 * <ul>
 *   <li>law-fetch : Extraction métadonnées depuis sgg.gouv.bj</li>
 *   <li>law-download : Téléchargement PDFs</li>
 *   <li>law-json-config : Orchestration conversion PDF → JSON</li>
 *   <li>law-consolidate : Consolidation et déduplication</li>
 * </ul>
 * 
 * <p>Jobs disponibles :
 * <ul>
 *   <li>fetchCurrentJob : Récupération documents courants</li>
 *   <li>fetchPreviousJob : Récupération documents antérieurs</li>
 *   <li>downloadJob : Téléchargement PDFs</li>
 *   <li>ocrJob : Extraction OCR des PDFs (law-pdf-ocr)</li>
 *   <li>ocrJsonJob : Extraction JSON depuis OCR (law-ocr-json)</li>
 *   <li>jsonConversionJob : Pipeline complet PDF → OCR → JSON</li>
 *   <li>consolidateJob : Consolidation finale</li>
 * </ul>
 * 
 * @author Law Team
 * @version 2.0.0
 * @since 2024-12-19
 */
@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = {
    "bj.gouv.sgg",           // law-app
    "bj.gouv.sgg.common",    // law-common
    "bj.gouv.sgg.fetch",     // law-fetch
    "bj.gouv.sgg.download",  // law-download
    "bj.gouv.sgg.config",    // law-json-config
    "bj.gouv.sgg.batch",     // law-consolidate
    "bj.gouv.sgg.ocr",       // law-pdf-ocr
    "bj.gouv.sgg.ocrjson"    // law-ocr-json
})
public class LawOrchestratorApplication {

    public static void main(String[] args) {
        log.info("==================================================");
        log.info("  LAW ORCHESTRATOR APPLICATION - STARTING");
        log.info("==================================================");
        
        SpringApplication.run(LawOrchestratorApplication.class, args);
        
        log.info("==================================================");
        log.info("  LAW ORCHESTRATOR APPLICATION - STOPPED");
        log.info("==================================================");
    }
}
