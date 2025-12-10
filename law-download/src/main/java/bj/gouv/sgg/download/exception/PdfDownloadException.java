package bj.gouv.sgg.download.exception;

import bj.gouv.sgg.exception.LawProcessingException;

/**
 * Exception levée lors d'erreurs de téléchargement de PDF.
 */
public class PdfDownloadException extends LawProcessingException {
    
    public PdfDownloadException(String message) {
        super(message);
    }
    
    public PdfDownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
