package bj.gouv.sgg.app;

import bj.gouv.sgg.job.OcrJob;
import lombok.extern.slf4j.Slf4j;

/**
 * Application de test fonctionnel pour l'OCR.
 * Permet de tester le OcrJob sans passer par law-app.
 */
@Slf4j
public class OcrApp {

    public static void main(String[] args) {
        log.info("=".repeat(60));
        log.info("🚀 Lancement du OCR App...");
        log.info("=".repeat(60));
        
        OcrJob ocrJob = new OcrJob();
        
        try {
            // Test 1: OCR d'un document spécifique
//            log.info("\n📄 Test 1: OCR document spécifique");
//            ocrJob.runDocument("loi-2012-43");
            
            // Test 2: OCR de plusieurs documents d'un type
//            log.info("\n📄 Test 2: OCR type 'loi'");
            ocrJob.run("loi");
            
            // Test 3: OCR décrets
//            log.info("\n📄 Test 3: OCR type 'decret'");
            ocrJob.run("decret");
            
        } catch (Exception e) {
            log.error("❌ Erreur lors de l'OCR", e);
        } finally {
            ocrJob.shutdown();
        }
        
        log.info("=".repeat(60));
        log.info("✅ OCR App terminé.");
        log.info("=".repeat(60));
    }
}
