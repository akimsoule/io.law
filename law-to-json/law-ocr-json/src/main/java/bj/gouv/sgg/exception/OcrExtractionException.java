package bj.gouv.sgg.exception;

import bj.gouv.sgg.exception.LawProcessingException;

/**
 * Exception levée lors de l'extraction d'articles depuis un texte OCR.
 * Utilisée pour signaler les erreurs pendant le parsing regex ou l'application de corrections.
 * Cette exception est non-bloquante car catchée par ArticleRegexExtractor.
 */
public class OcrExtractionException extends LawProcessingException {
    
    public OcrExtractionException(String message) {
        super(message);
    }
    
    public OcrExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public OcrExtractionException(String documentId, String errorCode, String message) {
        super(documentId, errorCode, message);
    }
}
