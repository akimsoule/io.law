package bj.gouv.sgg.exception;

/**
 * Exception levée quand un document n'est pas trouvé sur le serveur (404).
 */
public class DocumentNotFoundException extends LawProcessingException {
    
    public DocumentNotFoundException(String documentId) {
        super(documentId, "DOCUMENT_NOT_FOUND", 
              String.format("Document %s not found on server (HTTP 404)", documentId));
    }
    
    public DocumentNotFoundException(String documentId, String url) {
        super(documentId, "DOCUMENT_NOT_FOUND", 
              String.format("Document %s not found at URL: %s", documentId, url));
    }
}
