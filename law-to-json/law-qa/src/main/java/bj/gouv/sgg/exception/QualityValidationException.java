package bj.gouv.sgg.exception;

/**
 * Exception levée lors d'erreurs de validation qualité.
 * 
 * @author io.law
 * @since 1.0.0
 */
public class QualityValidationException extends RuntimeException {
    
    public QualityValidationException(String message) {
        super(message);
    }
    
    public QualityValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
