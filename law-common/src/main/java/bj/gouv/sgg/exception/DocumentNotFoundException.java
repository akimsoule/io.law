package bj.gouv.sgg.exception;

/**
 * Exception levée quand un document n'est pas trouvé.
 */
public class DocumentNotFoundException extends LawProcessingException {
    
    public DocumentNotFoundException(String message) {
        super(message);
    }
    
    public DocumentNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
