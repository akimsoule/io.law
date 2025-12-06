package bj.gouv.sgg.exception;

/**
 * Exception levée lors d'opérations sur les fichiers (lecture, écriture, transformation)
 */
public class FileOperationException extends RuntimeException {
    
    public FileOperationException(String message) {
        super(message);
    }
    
    public FileOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
