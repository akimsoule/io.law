package bj.gouv.sgg.exception;

/**
 * Exception levée lors d'une erreur de traitement OCR.
 */
public class OcrProcessingException extends LawProcessingException {
    
    public OcrProcessingException(String documentId, String message) {
        super(documentId, "OCR_PROCESSING_ERROR", message);
    }
    
    public OcrProcessingException(String documentId, String message, Throwable cause) {
        super(documentId, "OCR_PROCESSING_ERROR", message, cause);
    }
    
    public OcrProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
