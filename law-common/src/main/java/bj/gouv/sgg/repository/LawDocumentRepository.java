package bj.gouv.sgg.repository;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.model.ProcessingStatus;

import java.util.List;
import java.util.Optional;

/**
 * Repository JPA pour les documents légaux.
 * Remplace JsonStorage pour garantir l'intégrité des données.
 */
public interface LawDocumentRepository {
    
    /**
     * Trouve un document par son ID technique.
     */
    Optional<LawDocumentEntity> findById(Long id);
    
    /**
     * Trouve un document par son identifiant métier (ex: "loi-2024-15").
     */
    Optional<LawDocumentEntity> findByDocumentId(String documentId);
    
    /**
     * Trouve un document par type, année et numéro.
     */
    Optional<LawDocumentEntity> findByTypeAndYearAndNumber(String type, int year, String number);
    
    /**
     * Vérifie l'existence d'un document par son identifiant métier.
     */
    boolean existsByDocumentId(String documentId);
    
    /**
     * Vérifie l'existence d'un document par type, année et numéro.
     */
    boolean existsByTypeAndYearAndNumber(String type, int year, int number);
    
    /**
     * Trouve tous les documents avec un statut donné.
     */
    List<LawDocumentEntity> findByStatus(ProcessingStatus status);
    
    /**
     * Trouve tous les documents d'un type et année donnés.
     */
    List<LawDocumentEntity> findByTypeAndYear(String type, int year);
    
    /**
     * Trouve tous les documents fetchés d'un type sur une plage d'années.
     * Optimisé pour éviter les multiples requêtes en boucle.
     * 
     * @param type Type de document ("loi" ou "decret")
     * @param minYear Année minimale (inclusive)
     * @param maxYear Année maximale (inclusive)
     * @return Liste des documents avec status success sur la plage d'années
     */
    List<LawDocumentEntity> findFetchedByTypeAndYearRange(String type, int minYear, int maxYear);
    
    /**
     * Compte les documents avec un statut donné.
     */
    long countByStatus(ProcessingStatus status);
    
    /**
     * Trouve tous les documents.
     */
    List<LawDocumentEntity> findAll();
    
    /**
     * Sauvegarde un document (insert ou update).
     */
    LawDocumentEntity save(LawDocumentEntity entity);
    
    /**
     * Sauvegarde plusieurs documents en batch.
     */
    List<LawDocumentEntity> saveAll(List<LawDocumentEntity> entities);
    
    /**
     * Supprime un document.
     */
    void delete(LawDocumentEntity entity);
    
    /**
     * Supprime tous les documents.
     */
    void deleteAll();
}
