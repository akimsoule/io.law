package bj.gouv.sgg.service;

/**
 * Service de fetch des documents.
 * Vérifie l'existence des documents sur le site SGG.
 */
public interface FetchService {
    
    /**
     * Fetch un document spécifique.
     * 
     * @param documentId ID du document (ex: loi-2018-27)
     */
    void runDocument(String documentId);
    
    /**
     * Fetch tous les documents d'un type.
     * 
     * @param type Type de document (loi/decret)
     */
    void runType(String type);
}
