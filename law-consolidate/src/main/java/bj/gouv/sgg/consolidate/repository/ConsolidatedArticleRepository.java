package bj.gouv.sgg.consolidate.repository;

import bj.gouv.sgg.consolidate.model.ConsolidatedArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository pour l'entité ConsolidatedArticle.
 * Gère les opérations CRUD sur les articles consolidés.
 * 
 * <p><b>Idempotence</b> : Les opérations sont idempotentes grâce à la contrainte
 * {@code UNIQUE(documentId, articleIndex)}. En cas de doublon, JPA lance
 * une exception {@code DataIntegrityViolationException} que le service
 * peut gérer pour faire un UPDATE au lieu d'un INSERT.
 * 
 * @see ConsolidatedArticle
 */
@Repository
public interface ConsolidatedArticleRepository extends JpaRepository<ConsolidatedArticle, Long> {
    
    /**
     * Trouve tous les articles d'un document donné, triés par index.
     * 
     * @param documentId ID du document (ex: "loi-2024-15")
     * @return Liste des articles triés par articleIndex ASC
     */
    List<ConsolidatedArticle> findByDocumentIdOrderByArticleIndexAsc(String documentId);
    
    /**
     * Trouve un article spécifique d'un document.
     * 
     * @param documentId ID du document (ex: "loi-2024-15")
     * @param articleIndex Numéro de l'article (1, 2, 3...)
     * @return Article si trouvé, sinon Optional.empty()
     */
    Optional<ConsolidatedArticle> findByDocumentIdAndArticleIndex(String documentId, Integer articleIndex);
    
    /**
     * Compte le nombre d'articles d'un document.
     * 
     * @param documentId ID du document (ex: "loi-2024-15")
     * @return Nombre d'articles
     */
    long countByDocumentId(String documentId);
    
    /**
     * Trouve tous les articles d'un type de document (loi ou decret).
     * 
     * @param documentType Type de document (loi, decret)
     * @return Liste des articles du type donné
     */
    List<ConsolidatedArticle> findByDocumentType(String documentType);
    
    /**
     * Trouve tous les articles d'une année donnée.
     * 
     * @param year Année du document (ex: 2024)
     * @return Liste des articles de l'année donnée
     */
    List<ConsolidatedArticle> findByDocumentYear(Integer year);
    
    /**
     * Trouve tous les articles avec une confiance >= seuil donné.
     * Permet filtrer articles haute qualité.
     * 
     * @param minConfidence Confiance minimum (0.0 à 1.0)
     * @return Liste des articles avec confiance >= minConfidence
     */
    @Query("SELECT a FROM ConsolidatedArticle a WHERE a.extractionConfidence >= :minConfidence ORDER BY a.documentYear DESC, a.documentNumber DESC, a.articleIndex ASC")
    List<ConsolidatedArticle> findHighConfidenceArticles(@Param("minConfidence") Double minConfidence);
    
    /**
     * Vérifie si un article existe déjà.
     * 
     * @param documentId ID du document (ex: "loi-2024-15")
     * @param articleIndex Numéro de l'article (1, 2, 3...)
     * @return true si l'article existe, false sinon
     */
    boolean existsByDocumentIdAndArticleIndex(String documentId, Integer articleIndex);
    
    /**
     * Supprime tous les articles d'un document.
     * Utile pour re-consolidation complète.
     * 
     * @param documentId ID du document (ex: "loi-2024-15")
     */
    void deleteByDocumentId(String documentId);
    
    /**
     * Compte le nombre total d'articles consolidés.
     * 
     * @return Nombre total d'articles en base
     */
    @Query("SELECT COUNT(a) FROM ConsolidatedArticle a")
    long countAllArticles();
}
