package bj.gouv.sgg.exception;

/**
 * Exception levée lors d'une erreur HTTP pendant le fetch.
 * Cette exception est non-bloquante car catchée par AbstractFetchService.
 */
public class FetchHttpException extends RuntimeException {
    
    private final String url;
    private final int statusCode;
    
    public FetchHttpException(String url, int statusCode) {
        super(String.format("HTTP error %d for %s", statusCode, url));
        this.url = url;
        this.statusCode = statusCode;
    }
    
    public FetchHttpException(String url, Throwable cause) {
        super(String.format("HTTP error for %s: %s", url, cause.getMessage()), cause);
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
