package bj.gouv.sgg.service;

/**
 * Service de consolidation des documents JSON extraits.
 * Valide et marque les documents comme consolidés.
 */
public interface ConsolidationService {
    
    /**
     * Consolide un document spécifique.
     * 
     * @param documentId ID du document (ex: loi-2018-27)
     */
    void runDocument(String documentId);
    
    /**
     * Consolide tous les documents d'un type.
     * 
     * @param type Type de document (loi/decret)
     */
    void runType(String type);
}
