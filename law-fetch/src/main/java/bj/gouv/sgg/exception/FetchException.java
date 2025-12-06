package bj.gouv.sgg.exception;

/**
 * Exception levée lors des erreurs de récupération HTTP des métadonnées de documents
 */
public class FetchException extends LawProcessingException {
    
    public FetchException(String message) {
        super(message);
    }
    
    public FetchException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public FetchException(String documentId, String url, int statusCode) {
        super(String.format("Failed to fetch document %s from %s (HTTP %d)", documentId, url, statusCode));
    }
    
    public FetchException(String documentId, String url, Throwable cause) {
        super(String.format("Failed to fetch document %s from %s: %s", documentId, url, cause.getMessage()), cause);
    }
}
