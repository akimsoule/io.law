package bj.gouv.sgg.exception;

/**
 * Exception levée lors d'un problème de vérification de hash SHA-256.
 * Cette exception est non-bloquante car catchée par DownloadServiceImpl.
 */
public class DownloadHashException extends DownloadException {
    
    private final String documentId;
    private final String expectedHash;
    private final String actualHash;
    
    public DownloadHashException(String documentId, String expectedHash, String actualHash) {
        super(documentId, "HASH_MISMATCH", 
              String.format("Hash mismatch for %s: expected=%s, actual=%s", documentId, expectedHash, actualHash));
        this.documentId = documentId;
        this.expectedHash = expectedHash;
        this.actualHash = actualHash;
    }
    
    public String getDocumentId() {
        return documentId;
    }
    
    public String getExpectedHash() {
        return expectedHash;
    }
    
    public String getActualHash() {
        return actualHash;
    }
}
