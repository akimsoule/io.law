package bj.gouv.sgg.app;

import bj.gouv.sgg.job.ArticleExtractionJob;
import lombok.extern.slf4j.Slf4j;

/**
 * Application de test fonctionnel pour l'extraction d'articles depuis OCR.
 * Permet de tester le ArticleExtractionJob sans passer par law-app.
 */
@Slf4j
public class ArticleExtractionApp {

    public static void main(String[] args) {
        log.info("=".repeat(60));
        log.info("🚀 Lancement du ArticleExtraction App...");
        log.info("=".repeat(60));
        
        ArticleExtractionJob extractionJob = new ArticleExtractionJob();
        
        try {
            // Test 1: Extraire un document spécifique
//            log.info("\n📄 Test 1: Extraction document spécifique");
//            extractionJob.runDocument("loi-2024-15");
            
            // Test 2: Extraire plusieurs documents d'un type
            log.info("\n📄 Test 2: Extraction type 'loi'");
            extractionJob.run("loi");
            
            // Test 3: Extraire décrets
//            log.info("\n📄 Test 3: Extraction type 'decret'");
//            extractionJob.run("decret");
            
        } catch (Exception e) {
            log.error("❌ Erreur lors de l'extraction", e);
        } finally {
            extractionJob.shutdown();
        }
        
        log.info("=".repeat(60));
        log.info("✅ ArticleExtraction App terminé.");
        log.info("=".repeat(60));
    }
}
