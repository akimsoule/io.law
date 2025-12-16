package bj.gouv.sgg.job.download;

import bj.gouv.sgg.service.DownloadService;
import bj.gouv.sgg.service.impl.DownloadServiceImpl;
import lombok.extern.slf4j.Slf4j;

/**
 * Job de téléchargement sans Spring Batch.
 * Télécharge les PDFs des documents FETCHED.
 * 
 * Ce job délègue toute la logique au DownloadService.
 */
@Slf4j
public class DownloadJob {
    
    private final DownloadService downloadService;
    
    public DownloadJob() {
        this.downloadService = DownloadServiceImpl.getInstance();
    }
    
    /**
     * Télécharge un document spécifique (mode ciblé).
     * Thread-safe pour exécution concurrente.
     * 
     * @param documentId ID du document (ex: loi-2018-27)
     */
    public synchronized void runDocument(String documentId) {
        downloadService.runDocument(documentId);
    }
    
    /**
     * Télécharge tous les documents FETCHED d'un type.
     * 
     * @param type Type de document (loi/decret)
     * @param maxDocuments Nombre maximum de documents à télécharger
     */
    public void run(String type, int maxDocuments) {
        downloadService.runType(type);
    }
    
    /**
     * Ferme les ressources.
     */
    public void shutdown() {
        log.info("🛑 DownloadJob shutdown");
    }
}
