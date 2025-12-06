package bj.gouv.sgg.exception;

/**
 * Exception levée lors d'erreurs d'extraction d'articles.
 */
public class ArticleExtractionException extends LawProcessingException {
    
    public ArticleExtractionException(String message) {
        super(message);
    }
    
    public ArticleExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
