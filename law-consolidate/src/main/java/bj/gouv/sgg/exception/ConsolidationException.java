package bj.gouv.sgg.exception;

/**
 * Exception levée lors d'erreurs de consolidation.
 */
public class ConsolidationException extends RuntimeException {
    
    public ConsolidationException(String message) {
        super(message);
    }
    
    public ConsolidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
