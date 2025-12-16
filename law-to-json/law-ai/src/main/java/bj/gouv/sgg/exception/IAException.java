package bj.gouv.sgg.exception;

import bj.gouv.sgg.exception.LawProcessingException;

/**
 * Exception générique pour les erreurs liées aux providers IA.
 * Cette exception est non-bloquante car catchée par PdfToJsonProcessor.
 */
public class IAException extends LawProcessingException {
    
    public IAException(String message) { 
        super(message); 
    }
    
    public IAException(String message, Throwable cause) { 
        super(message, cause); 
    }
    
    public IAException(String documentId, String errorCode, String message) {
        super(documentId, errorCode, message);
    }
    
    public IAException(String documentId, String errorCode, String message, Throwable cause) {
        super(documentId, errorCode, message, cause);
    }
}
