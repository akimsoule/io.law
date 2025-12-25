package bj.gouv.sgg.service;

import bj.gouv.sgg.job.JobOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Service d'orchestration en continu.
 * S'assure que fetchCurrentJob est exécuté max 1 fois par jour.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestrationService {
    
    private final JobOrchestrator orchestrator;
    private LocalDate lastFetchCurrentDate = null;
    
    /**
     * Lance l'orchestration en boucle continue.
     * fetchCurrentJob : max 1 fois par jour
     * Autres jobs : en continu
     * 
     * @param type Type de document (loi ou decret)
     * @param skipFetchDaily Si true, skip fetchCurrent si déjà exécuté aujourd'hui
     * @throws Exception si erreur critique
     */
    public void runContinuousOrchestration(String type, boolean skipFetchDaily) throws Exception {
        log.info("🔄 Démarrage orchestration continue pour type={}", type);
        log.info("⚙️  Skip fetchCurrent si déjà exécuté: {}", skipFetchDaily);
        
        int cycle = 0;
        
        while (true) {
            cycle++;
            LocalDate today = LocalDate.now();
            
            log.info("\n");
            log.info("╔═══════════════════════════════════════════════════════╗");
            log.info("║  CYCLE #{} - {}                                       ║", cycle, today);
            log.info("╚═══════════════════════════════════════════════════════╝");
            log.info("\n");
            
            Map<String, String> params = new HashMap<>();
            params.put("type", type);
            params.put("documentId", "ALL");
            
            try {
                // 1. fetchCurrentJob (si nouveau jour ou si skip désactivé)
                boolean shouldRunFetchCurrent = !skipFetchDaily || 
                    lastFetchCurrentDate == null || 
                    !lastFetchCurrentDate.equals(today);
                
                if (shouldRunFetchCurrent) {
                    log.info("\n[1/6] 📥 FETCH CURRENT - Année courante");
                    orchestrator.runJob("fetchCurrentJob", params);
                    lastFetchCurrentDate = today;
                } else {
                    log.info("\n[1/6] ⏭️  FETCH CURRENT - Skipped (déjà exécuté aujourd'hui)");
                }
                
                // 2. fetchPreviousJob (toujours)
                log.info("\n[2/6] 📥 FETCH PREVIOUS - Années précédentes");
                orchestrator.runJob("fetchPreviousJob", params);
                
                // 3. downloadJob
                log.info("\n[3/8] ⬇️  DOWNLOAD - Téléchargement PDFs");
                orchestrator.runJob("downloadJob", params);
                
                // 4. ocrJob
                log.info("\n[4/8] 🔍 OCR - Extraction texte");
                orchestrator.runJob("ocrJob", params);
                
                // 5. ocrJsonJob
                log.info("\n[5/8] 📄 OCR JSON - Structuration JSON");
                orchestrator.runJob("ocrJsonJob", params);
                
                // 6. pdfToImagesJob
                log.info("\n[6/8] 🖼️  PDF→IMAGES - Conversion PDF → Images");
                orchestrator.runJob("pdfToImagesJob", params);
                
                // 7. jsonConversionJob
                log.info("\n[7/8] 🔧 JSON CONVERSION - Extraction complète");
                orchestrator.runJob("jsonConversionJob", params);
                
                // 8. consolidateJob
                log.info("\n[8/8] ✅ CONSOLIDATE - Consolidation finale");
                orchestrator.runJob("consolidateJob", params);
                
                log.info("\n✅ Cycle #{} terminé avec succès", cycle);
                
                // Pause entre cycles
                log.info("⏸️  Pause 60s avant prochain cycle...");
                Thread.sleep(60_000);
                
            } catch (InterruptedException ie) {
                // Respect the interrupt: re-interrupt and exit the loop/service
                Thread.currentThread().interrupt();
                log.info("⏹️  Orchestration interrompue, sortie du service.");
                return;
            } catch (Exception e) {
                log.error("❌ Erreur dans cycle #{}: {}", cycle, e.getMessage(), e);
                log.info("⏸️  Pause 120s avant retry...");
                try {
                    Thread.sleep(120_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.info("⏹️  Orchestration interrompue pendant le retry sleep, sortie du service.");
                    return;
                }
            }
        }
    }
}
