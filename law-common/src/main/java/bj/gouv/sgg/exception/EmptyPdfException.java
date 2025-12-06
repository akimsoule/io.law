package bj.gouv.sgg.exception;

/**
 * Exception levée quand un PDF téléchargé est vide.
 */
public class EmptyPdfException extends LawProcessingException {
    
    public EmptyPdfException(String documentId) {
        super(documentId, "EMPTY_PDF", String.format("Downloaded PDF is empty for document: %s", documentId));
    }
    
    public EmptyPdfException(String documentId, String url) {
        super(documentId, "EMPTY_PDF", String.format("Downloaded PDF is empty for document %s from URL: %s", documentId, url));
    }
}
