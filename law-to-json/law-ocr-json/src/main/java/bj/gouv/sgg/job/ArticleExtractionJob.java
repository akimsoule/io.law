package bj.gouv.sgg.job;

import bj.gouv.sgg.service.ArticleExtractionService;
import bj.gouv.sgg.service.impl.ArticleExtractionServiceImpl;
import lombok.extern.slf4j.Slf4j;

/**
 * Job d'extraction d'articles depuis OCR.
 * Délègue toute la logique au ArticleExtractionService.
 * 
 * @see ArticleExtractionServiceImpl
 */
@Slf4j
public class ArticleExtractionJob {
    
    private final ArticleExtractionService articleExtractionService;
    
    public ArticleExtractionJob() {
        this.articleExtractionService = ArticleExtractionServiceImpl.getInstance();
    }
    
    /**
     * Extrait les articles d'un document spécifique (mode ciblé).
     * Thread-safe pour exécution concurrente.
     * 
     * @param documentId ID du document (ex: loi-2018-27)
     */
    public synchronized void runDocument(String documentId) {
        articleExtractionService.runDocument(documentId);
    }
    
    /**
     * Extrait les articles de tous les documents OCR d'un type.
     * 
     * @param type Type de document (loi/decret)
     */
    public void run(String type) {
        articleExtractionService.runType(type);
    }
    
    /**
     * Ferme les ressources.
     */
    public void shutdown() {
        log.info("🛑 ArticleExtractionJob shutdown");
    }
}
