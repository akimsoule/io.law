package bj.gouv.sgg.service.impl;

import bj.gouv.sgg.entity.FetchCursorEntity;
import bj.gouv.sgg.service.FetchCursorService;
import bj.gouv.sgg.service.FetchPreviousService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Implémentation du service de fetch pour les années précédentes.
 * Étend AbstractFetchService pour réutiliser la logique commune.
 * Gère le cursor pour reprendre le scan là où il s'est arrêté.
 */
@Slf4j
public class FetchPreviousServiceImpl extends AbstractFetchService implements FetchPreviousService {
    
    private static FetchPreviousServiceImpl instance;
    private final FetchCursorService cursorService;
    
    private FetchPreviousServiceImpl() {
        super();
        this.cursorService = new FetchCursorService();
    }
    
    public static synchronized FetchPreviousServiceImpl getInstance() {
        if (instance == null) {
            instance = new FetchPreviousServiceImpl();
        }
        return instance;
    }
    
    @Override
    public void runType(String type) {
        // Par défaut, utilise maxItems du config
        run(type, config.getMaxItemsToFetchPrevious());
    }
    
    @Override
    public void run(String type, int maxItems) {
        int currentYear = LocalDate.now().getYear();
        
        // Charger le cursor existant ou partir de (currentYear-1, 1)
        Optional<FetchCursorEntity> cursorOpt = cursorService.getCursor(type, "fetch-previous");
        
        int startYear;
        int startNumber;
        
        if (cursorOpt.isPresent()) {
            FetchCursorEntity cursor = cursorOpt.get();
            startYear = cursor.getCurrentYear();
            startNumber = cursor.getCurrentNumber();
            log.info("🔄 Reprise depuis cursor: type={}, year={}, number={}", 
                     type, startYear, startNumber);
        } else {
            startYear = currentYear - 1;
            startNumber = 1;
            log.info("🆕 Nouveau scan: type={}, years=1960-{}, maxItems={}", 
                     type, startYear, maxItems);
        }
        
        int count = 0;
        int found = 0;
        
        // Générer et traiter documents des années précédentes
        boolean stop = false;
        for (int year = startYear; year >= 1960 && !stop; year--) {
            int numStart = (year == startYear) ? startNumber : 1;
            
            for (int num = numStart; num <= 2000; num++) {
                if (count >= maxItems) {
                    log.info("⏹️ Limite atteinte: {} documents vérifiés", maxItems);
                    // Sauvegarder position actuelle avant de s'arrêter
                    saveCursor(type, year, num);
                    stop = true;
                    break;
                }
                
                String documentId = String.format("%s-%d-%d", type, year, num);
                
                try {
                    runDocument(documentId);
                    found++;
                } catch (Exception e) {
                    log.debug("Document {} non trouvé ou erreur", documentId);
                }
                
                count++;
                
                // Sauvegarder cursor tous les 100 documents
                if (count % 100 == 0) {
                    saveCursor(type, year, num);
                }
            }
        }
        
        log.info("✅ FetchPrevious terminé: {} documents vérifiés, {} trouvés", count, found);
    }
    
    /**
     * Sauvegarde la position actuelle du cursor
     */
    private void saveCursor(String type, int year, int number) {
        try {
            cursorService.updateCursor(type, "fetch-previous", year, number);
        } catch (Exception e) {
            log.warn("⚠️ Erreur lors de la sauvegarde du cursor: {}", e.getMessage());
        }
    }
}
