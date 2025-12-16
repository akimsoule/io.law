package bj.gouv.sgg.exception;

/**
 * Exception générique pour les erreurs de téléchargement.
 * Cette exception est non-bloquante car catchée par DownloadServiceImpl.
 */
public class DownloadException extends LawProcessingException {
    
    public DownloadException(String message) {
        super(message);
    }
    
    public DownloadException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public DownloadException(String documentId, String errorCode, String message) {
        super(documentId, errorCode, message);
    }
    
    public DownloadException(String documentId, String errorCode, String message, Throwable cause) {
        super(documentId, errorCode, message, cause);
    }
}
