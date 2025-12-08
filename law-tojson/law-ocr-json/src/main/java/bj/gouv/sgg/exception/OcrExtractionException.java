package bj.gouv.sgg.exception;

/**
 * Exception levée lors de l'extraction d'articles depuis un texte OCR.
 * Utilisée pour signaler les erreurs pendant le parsing regex ou l'application de corrections.
 */
public class OcrExtractionException extends RuntimeException {
    
    public OcrExtractionException(String message) {
        super(message);
    }
    
    public OcrExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
