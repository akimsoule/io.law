package bj.gouv.sgg.service;

/**
 * Service d'extraction d'articles depuis OCR.
 * Suit le pattern Reader-Processor-Writer.
 */
public interface ArticleExtractionService {
    
    /**
     * Extrait les articles d'un document spécifique.
     * 
     * @param documentId ID du document (ex: loi-2018-27)
     */
    void runDocument(String documentId);
    
    /**
     * Extrait les articles de tous les documents OCR d'un type.
     * 
     * @param type Type de document (loi/decret)
     */
    void runType(String type);
}
