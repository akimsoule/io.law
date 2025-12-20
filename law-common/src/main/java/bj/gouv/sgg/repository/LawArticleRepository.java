package bj.gouv.sgg.repository;

import bj.gouv.sgg.entity.LawArticleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository JPA pour gérer les articles consolidés.
 */
@Repository
public interface LawArticleRepository extends JpaRepository<LawArticleEntity, Long> {

    /**
     * Récupère tous les articles d'un document.
     */
    List<LawArticleEntity> findByDocumentIdOrderByArticleNumber(String documentId);

    /**
     * Supprime tous les articles d'un document.
     */
    void deleteByDocumentId(String documentId);

    /**
     * Compte le nombre d'articles d'un document.
     */
    long countByDocumentId(String documentId);
}
