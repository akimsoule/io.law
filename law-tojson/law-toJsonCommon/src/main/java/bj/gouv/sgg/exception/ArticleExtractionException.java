package bj.gouv.sgg.exception;

/**
 * Exception levée lors d'une erreur d'extraction d'articles.
 */
public class ArticleExtractionException extends LawProcessingException {
    
    public ArticleExtractionException(String documentId, String message) {
        super(documentId, "ARTICLE_EXTRACTION_ERROR", message);
    }
    
    public ArticleExtractionException(String documentId, String message, Throwable cause) {
        super(documentId, "ARTICLE_EXTRACTION_ERROR", message, cause);
    }
}
