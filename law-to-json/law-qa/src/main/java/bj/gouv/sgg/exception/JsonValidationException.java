package bj.gouv.sgg.exception;

/**
 * Exception levée lors d'erreurs de validation JSON.
 * 
 * @author io.law
 * @since 1.0.0
 */
public class JsonValidationException extends RuntimeException {
    
    public JsonValidationException(String message) {
        super(message);
    }
    
    public JsonValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
