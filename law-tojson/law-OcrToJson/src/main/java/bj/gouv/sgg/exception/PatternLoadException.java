package bj.gouv.sgg.exception;

/**
 * Exception levée lors du chargement des patterns regex depuis patterns.properties
 */
public class PatternLoadException extends RuntimeException {
    
    public PatternLoadException(String message) {
        super(message);
    }
    
    public PatternLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
