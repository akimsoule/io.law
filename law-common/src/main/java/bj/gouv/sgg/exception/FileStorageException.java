package bj.gouv.sgg.exception;

/**
 * Exception levée lors d'une erreur d'opération sur le système de fichiers.
 */
public class FileStorageException extends LawProcessingException {
    
    private final String filePath;
    private final OperationType operationType;
    
    public enum OperationType {
        READ, WRITE, DELETE, CREATE
    }
    
    public FileStorageException(String filePath, OperationType operationType, String message) {
        super(String.format("File storage error [%s] on %s: %s", operationType, filePath, message));
        this.filePath = filePath;
        this.operationType = operationType;
    }
    
    public FileStorageException(String filePath, OperationType operationType, String message, Throwable cause) {
        super(String.format("File storage error [%s] on %s: %s", operationType, filePath, message), cause);
        this.filePath = filePath;
        this.operationType = operationType;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public OperationType getOperationType() {
        return operationType;
    }
}
