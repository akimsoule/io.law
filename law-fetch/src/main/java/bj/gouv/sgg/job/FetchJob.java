package bj.gouv.sgg.job.fetch;

import bj.gouv.sgg.service.FetchCurrentService;
import bj.gouv.sgg.service.FetchPreviousService;
import bj.gouv.sgg.service.FetchService;
import bj.gouv.sgg.service.impl.FetchCurrentServiceImpl;
import bj.gouv.sgg.service.impl.FetchPreviousServiceImpl;
import lombok.extern.slf4j.Slf4j;

/**
 * Job de fetch sans Spring Batch.
 * Récupère les métadonnées des documents depuis le site SGG.
 * 
 * Ce job délègue toute la logique aux services spécialisés.
 */
@Slf4j
public class FetchJob {
    
    private final FetchService fetchService;
    private final FetchCurrentService fetchCurrentService;
    private final FetchPreviousService fetchPreviousService;
    
    public FetchJob() {
        // Les services étendent AbstractFetchService
        this.fetchCurrentService = FetchCurrentServiceImpl.getInstance();
        this.fetchPreviousService = FetchPreviousServiceImpl.getInstance();
        // Utiliser fetchCurrentService pour runDocument (cast vers FetchService)
        this.fetchService = (FetchService) fetchCurrentService;
    }
    
    /**
     * Exécute le fetch pour un document spécifique (mode ciblé).
     * Thread-safe pour exécution concurrente.
     * 
     * @param documentId ID du document (ex: loi-2018-27)
     */
    public synchronized void runDocument(String documentId) {
        fetchService.runDocument(documentId);
    }
    
    /**
     * Exécute le fetch pour l'année courante (mode current).
     * 
     * @param type Type de document (loi/decret)
     */
    public void runCurrent(String type) {
        fetchCurrentService.run(type);
    }
    
    /**
     * Exécute le fetch pour les années précédentes (mode previous).
     * 
     * @param type Type de document (loi/decret)
     * @param maxItems Nombre maximum de documents à traiter
     */
    public void runPrevious(String type, int maxItems) {
        fetchPreviousService.run(type, maxItems);
    }
    
    /**
     * Ferme les ressources.
     */
    public void shutdown() {
        log.info("🛑 FetchJob shutdown");
    }
}
