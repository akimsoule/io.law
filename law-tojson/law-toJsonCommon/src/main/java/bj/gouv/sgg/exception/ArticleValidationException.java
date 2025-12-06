package bj.gouv.sgg.exception;

/**
 * Exception levée lors de la validation d'articles extraits.
 */
public class ArticleValidationException extends RuntimeException {
    public ArticleValidationException() { super(); }
    public ArticleValidationException(String message) { super(message); }
    public ArticleValidationException(String message, Throwable cause) { super(message, cause); }
    public ArticleValidationException(Throwable cause) { super(cause); }
}
