package bj.gouv.sgg.exception;

/**
 * Exception levée lors d'une erreur HTTP pendant le téléchargement.
 * Cette exception est non-bloquante car catchée par DownloadServiceImpl.
 */
public class DownloadHttpException extends DownloadException {
    
    private final String url;
    private final int statusCode;
    
    public DownloadHttpException(String url, int statusCode) {
        super(String.format("HTTP error %d downloading from %s", statusCode, url));
        this.url = url;
        this.statusCode = statusCode;
    }
    
    public DownloadHttpException(String url, Throwable cause) {
        super(String.format("HTTP error downloading from %s: %s", url, cause.getMessage()), cause);
        this.url = url;
        this.statusCode = 0;
    }
    
    public String getUrl() {
        return url;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
}
