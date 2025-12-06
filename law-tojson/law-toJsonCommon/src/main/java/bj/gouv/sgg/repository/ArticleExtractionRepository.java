package bj.gouv.sgg.repository;

import bj.gouv.sgg.model.ArticleExtraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository pour gérer les articles extraits
 */
@Repository
public interface ArticleExtractionRepository extends JpaRepository<ArticleExtraction, Long> {
    
    List<ArticleExtraction> findByDocumentIdOrderByArticleIndex(String documentId);
    
    long countByDocumentId(String documentId);
    
    boolean existsByDocumentId(String documentId);
    
    @Query("SELECT DISTINCT a.documentId FROM ArticleExtraction a")
    List<String> findAllDocumentIds();
}
