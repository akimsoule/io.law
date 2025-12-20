package bj.gouv.sgg.service;

import bj.gouv.sgg.entity.LawArticleEntity;

import java.util.List;

/**
 * Service pour gérer les articles consolidés.
 */
public interface LawArticleService {

    /**
     * Sauvegarde un article consolidé.
     */
    LawArticleEntity save(LawArticleEntity article);

    /**
     * Sauvegarde une liste d'articles en batch.
     */
    List<LawArticleEntity> saveAll(List<LawArticleEntity> articles);

    /**
     * Récupère tous les articles d'un document.
     */
    List<LawArticleEntity> findByDocumentId(String documentId);

    /**
     * Supprime tous les articles d'un document.
     */
    void deleteByDocumentId(String documentId);

    /**
     * Compte le nombre d'articles d'un document.
     */
    long countByDocumentId(String documentId);
}
