package bj.gouv.sgg.exception;

import lombok.Getter;

/**
 * Exception de base pour toutes les erreurs métier de l'application.
 * Les exceptions spécifiques héritent de cette classe.
 */
@Getter
public class LawProcessingException extends RuntimeException {
    
    private final String documentId;
    private final String errorCode;
    
    public LawProcessingException(String message) {
        super(message);
        this.documentId = null;
        this.errorCode = null;
    }
    
    public LawProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.documentId = null;
        this.errorCode = null;
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

}
