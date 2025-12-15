package bj.gouv.sgg.storage;

/**
 * Exception levée lors d'erreurs de stockage JSON.
 */
public class StorageException extends RuntimeException {
    
    public StorageException(String message) {
        super(message);
    }
    
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
