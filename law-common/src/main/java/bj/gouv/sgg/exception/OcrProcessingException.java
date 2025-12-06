package bj.gouv.sgg.exception;

/**
 * Exception levée lors d'erreurs de traitement OCR.
 */
public class OcrProcessingException extends LawProcessingException {
    
    public OcrProcessingException(String message) {
        super(message);
    }
    
    public OcrProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
