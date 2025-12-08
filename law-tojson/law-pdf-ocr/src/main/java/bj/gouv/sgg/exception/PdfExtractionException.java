package bj.gouv.sgg.exception;

/**
 * Exception levée lors de l'extraction de texte depuis un PDF
 */
public class PdfExtractionException extends RuntimeException {
    public PdfExtractionException(String message) {
        super(message);
    }

    public PdfExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
