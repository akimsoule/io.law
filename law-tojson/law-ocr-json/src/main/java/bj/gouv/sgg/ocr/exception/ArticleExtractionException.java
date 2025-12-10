package bj.gouv.sgg.ocr.exception;

import bj.gouv.sgg.exception.LawProcessingException;

/**
 * Exception levée lors d'erreurs d'extraction d'articles depuis OCR.
 */
public class ArticleExtractionException extends LawProcessingException {
    
    public ArticleExtractionException(String message) {
        super(message);
    }
    
    public ArticleExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
