package bj.gouv.sgg.exception;

/**
 * Exception levée lors d'une erreur de téléchargement de PDF.
 */
public class PdfDownloadException extends LawProcessingException {
    
    public PdfDownloadException(String documentId, String message) {
        super(documentId, "PDF_DOWNLOAD_ERROR", message);
    }
    
    public PdfDownloadException(String documentId, String message, Throwable cause) {
        super(documentId, "PDF_DOWNLOAD_ERROR", message, cause);
    }
}
