package bj.gouv.sgg.service;

/**
 * Service de téléchargement des PDFs.
 * Suit le pattern Reader-Processor-Writer.
 */
public interface DownloadService {
    
    /**
     * Télécharge un document spécifique.
     * 
     * @param documentId ID du document (ex: loi-2018-27)
     */
    void runDocument(String documentId);
    
    /**
     * Télécharge tous les documents FETCHED d'un type.
     * 
     * @param type Type de document (loi/decret)
     */
    void runType(String type);
}
