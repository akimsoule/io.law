package bj.gouv.sgg.service;

/**
 * Service de téléchargement des PDFs.
 * Télécharge les documents depuis le site SGG.
 */
public interface DownloadService {
    
    /**
     * Télécharge un document spécifique.
     * 
     * @param documentId ID du document (ex: loi-2018-27)
     */
    void runDocument(String documentId);
    
    /**
     * Télécharge tous les documents d'un type.
     * 
     * @param type Type de document (loi/decret)
     */
    void runType(String type);
}
