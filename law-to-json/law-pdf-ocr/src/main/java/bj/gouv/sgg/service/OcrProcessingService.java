package bj.gouv.sgg.service;

/**
 * Service de traitement OCR avec pattern Reader-Processor-Writer.
 * Gère l'extraction OCR des PDFs vers fichiers texte.
 */
public interface OcrProcessingService {
    
    /**
     * Effectue l'OCR sur un document spécifique.
     * 
     * @param documentId ID du document (ex: loi-2018-27)
     */
    void runDocument(String documentId);
    
    /**
     * Effectue l'OCR sur tous les PDFs d'un type.
     * 
     * @param type Type de document (loi/decret)
     */
    void runType(String type);
}
