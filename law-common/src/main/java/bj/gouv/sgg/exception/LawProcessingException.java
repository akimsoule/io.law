package bj.gouv.sgg.exception;

/**
 * Exception de base pour toutes les exceptions métier de l'application.
 * Cette exception est non-bloquante car catchée dans les services.
 */
public class LawProcessingException extends RuntimeException {
    
    private final String documentId;
    private final String errorCode;
    
    public LawProcessingException(String message) {
        super(message);
        this.documentId = null;
        this.errorCode = "UNKNOWN";
    }
    
    public LawProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.documentId = null;
        this.errorCode = "UNKNOWN";
    }
    
    public LawProcessingException(String documentId, String errorCode, String message) {
        super(message);
        this.documentId = documentId;
        this.errorCode = errorCode;
    }
    
    public LawProcessingException(String documentId, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.documentId = documentId;
        this.errorCode = errorCode;
    }
    
    public String getDocumentId() {
        return documentId;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}
