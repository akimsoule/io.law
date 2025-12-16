package bj.gouv.sgg.job;

import bj.gouv.sgg.service.ConsolidationService;
import bj.gouv.sgg.service.impl.ConsolidationServiceImpl;
import lombok.extern.slf4j.Slf4j;

/**
 * Job de consolidation des documents avec JSON extrait.
 * 
 * Workflow :
 * 1. Scanne répertoire articles/{type}/
 * 2. Pour chaque fichier .json existant
 * 3. Vérifie que le document existe dans extracted.json
 * 4. Change le statut vers CONSOLIDATED
 * 
 * Ce job délègue toute la logique au ConsolidationService.
 */
@Slf4j
public class ConsolidateJob {
    
    private final ConsolidationService consolidationService;
    
    public ConsolidateJob() {
        this.consolidationService = ConsolidationServiceImpl.getInstance();
    }
    
    /**
     * Lance la consolidation pour un document spécifique (mode ciblé).
     * Thread-safe pour exécution concurrente.
     * 
     * @param documentId ID du document (ex: loi-2018-27)
     */
    public synchronized void runDocument(String documentId) {
        consolidationService.runDocument(documentId);
    }
    
    /**
     * Lance la consolidation pour un type de document.
     * 
     * @param type Type de document (loi/decret)
     */
    public void run(String type) {
        consolidationService.runType(type);
    }
}
