package bj.gouv.sgg.orchestrator;

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
            
            log.info("\n╔═══════════════════════════════════════════════════════╗");
            log.info("║  CYCLE #{} - {}                            ║", cycle, today);
            log.info("╚═══════════════════════════════════════════════════════╝");
            
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
                log.info("\n[3/6] ⬇️  DOWNLOAD - Téléchargement PDFs");
                orchestrator.runJob("downloadJob", params);
                
                // 4. ocrJob
                log.info("\n[4/6] 🔍 OCR - Extraction texte");
                orchestrator.runJob("ocrJob", params);
                
                // 5. ocrJsonJob
                log.info("\n[5/6] 📄 OCR JSON - Structuration JSON");
                orchestrator.runJob("ocrJsonJob", params);
                
                // 6. consolidateJob
                log.info("\n[6/6] ✅ CONSOLIDATE - Consolidation finale");
                orchestrator.runJob("consolidateJob", params);
                
                log.info("\n✅ Cycle #{} terminé avec succès", cycle);
                
                // Pause entre cycles
                log.info("⏸️  Pause 60s avant prochain cycle...");
                Thread.sleep(60_000);
                
            } catch (Exception e) {
                log.error("❌ Erreur dans cycle #{}: {}", cycle, e.getMessage(), e);
                log.info("⏸️  Pause 120s avant retry...");
                Thread.sleep(120_000);
            }
        }
    }
}
