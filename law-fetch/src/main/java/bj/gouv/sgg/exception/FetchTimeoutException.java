package bj.gouv.sgg.exception;

/**
 * Exception levée lors d'un timeout pendant le fetch.
 * Cette exception est non-bloquante car catchée par AbstractFetchService.
 */
public class FetchTimeoutException extends RuntimeException {
    
    private final String documentId;
    
    public FetchTimeoutException(String documentId) {
        super(String.format("Timeout fetching document %s", documentId));
        this.documentId = documentId;
    }
    
    public String getDocumentId() {
        return documentId;
    }
}
