package bj.gouv.sgg.exception;

/**
 * Exception levée quand un PDF téléchargé est vide.
 * Cette exception est non-bloquante car catchée par DownloadServiceImpl.
 */
public class DownloadEmptyPdfException extends DownloadException {
    
    public DownloadEmptyPdfException(String documentId) {
        super(documentId, "EMPTY_PDF", String.format("Downloaded PDF is empty for document: %s", documentId));
    }
    
    public DownloadEmptyPdfException(String documentId, String url) {
        super(documentId, "EMPTY_PDF", String.format("Downloaded PDF is empty for document %s from URL: %s", documentId, url));
    }
}
