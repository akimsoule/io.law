package bj.gouv.sgg.service.impl;

import bj.gouv.sgg.service.FetchCurrentService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;

/**
 * Implémentation du service de fetch pour l'année courante.
 * Étend AbstractFetchService pour réutiliser la logique commune.
 */
@Slf4j
public class FetchCurrentServiceImpl extends AbstractFetchService implements FetchCurrentService {
    
    private static FetchCurrentServiceImpl instance;
    
    private FetchCurrentServiceImpl() {
        super();
    }
    
    public static synchronized FetchCurrentServiceImpl getInstance() {
        if (instance == null) {
            instance = new FetchCurrentServiceImpl();
        }
        return instance;
    }
    
    @Override
    public void runType(String type) {
        run(type);
    }
    
    @Override
    public void run(String type) {
        int currentYear = LocalDate.now().getYear();
        log.info("🔍 FetchCurrent: type={}, year={}", type, currentYear);
        
        int total = 0;
        int found = 0;
        
        // Vérifier documents 1 à 2000 de l'année courante
        for (int num = 1; num <= 2000; num++) {
            String documentId = String.format("%s-%d-%d", type, currentYear, num);
            
            try {
                runDocument(documentId);
                found++;
            } catch (Exception e) {
                log.debug("Document {} non trouvé ou erreur", documentId);
            }
            
            total++;
        }
        
        log.info("✅ FetchCurrent terminé: {} documents vérifiés, {} trouvés", total, found);
    }
}
