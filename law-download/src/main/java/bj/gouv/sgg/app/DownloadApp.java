package bj.gouv.sgg.app;

import bj.gouv.sgg.job.download.DownloadJob;
import lombok.extern.slf4j.Slf4j;

/**
 * Application de test fonctionnel pour le téléchargement.
 * Permet de tester le DownloadJob sans passer par law-app.
 */
@Slf4j
public class DownloadApp {

    public static void main(String[] args) {
        log.info("=".repeat(60));
        log.info("🚀 Lancement du Download App...");
        log.info("=".repeat(60));
        
        DownloadJob downloadJob = new DownloadJob();
        
        try {
            // Test 1: Télécharger un document spécifique
//            log.info("\n📥 Test 1: Téléchargement document spécifique");
            downloadJob.runDocument("loi-2025-02");
            
            // Test 2: Télécharger plusieurs documents d'un type
//            log.info("\n📥 Test 2: Téléchargement type 'loi'");
//            downloadJob.run("loi");
//
            // Test 3: Télécharger décrets
//            log.info("\n📥 Test 3: Téléchargement type 'decret'");
//            downloadJob.run("decret");
            
        } catch (Exception e) {
            log.error("❌ Erreur lors du téléchargement", e);
        } finally {
            downloadJob.shutdown();
        }
        
        log.info("=".repeat(60));
        log.info("✅ Download App terminé.");
        log.info("=".repeat(60));
    }
}
