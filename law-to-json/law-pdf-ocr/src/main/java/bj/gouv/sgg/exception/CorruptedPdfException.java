package bj.gouv.sgg.exception;

/**
 * Exception levée quand un PDF est corrompu ou invalide.
 * Permet de distinguer les erreurs de PDF des erreurs d'OCR.
 */
public class CorruptedPdfException extends OcrProcessingException {
    
    public CorruptedPdfException(String documentId, String message) {
        super(documentId, message);
    }
    
    public CorruptedPdfException(String documentId, String message, Throwable cause) {
        super(documentId, message, cause);
    }
}
