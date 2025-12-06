package bj.gouv.sgg.exception;

/**
 * Exception levée quand le format du documentId est invalide.
 * Format attendu: {type}-{year}-{number} (ex: loi-2020-32, decret-2024-15)
 */
public class InvalidDocumentIdException extends LawProcessingException {
    
    public InvalidDocumentIdException(String documentId) {
        super(documentId, "INVALID_DOCUMENT_ID", 
              String.format("Invalid document ID format: %s. Expected format: {type}-{year}-{number} (ex: loi-2020-32)", documentId));
    }
    
    public InvalidDocumentIdException(String documentId, String details) {
        super(documentId, "INVALID_DOCUMENT_ID", 
              String.format("Invalid document ID: %s. %s", documentId, details));
    }
}
