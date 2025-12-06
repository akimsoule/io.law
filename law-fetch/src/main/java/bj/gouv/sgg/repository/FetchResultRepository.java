package bj.gouv.sgg.repository;

import bj.gouv.sgg.model.FetchResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository pour gérer les résultats de fetch en base de données
 */
@Repository
public interface FetchResultRepository extends JpaRepository<FetchResult, Long> {
    
    /**
     * Trouve un résultat par son documentId
     */
    Optional<FetchResult> findByDocumentId(String documentId);
    
    /**
     * Vérifie si un document existe par son documentId
     */
    boolean existsByDocumentId(String documentId);
    
    /**
     * Supprime un document par son documentId (mode force)
     * @Transactional géré par le caller
     */
    @org.springframework.transaction.annotation.Transactional
    void deleteByDocumentId(String documentId);
    
    /**
     * Récupère tous les documents trouvés
     */
    List<FetchResult> findByExistsTrue();
    
    /**
     * Récupère tous les documents non trouvés
     */
    List<FetchResult> findByExistsFalse();
    
    /**
     * Compte le nombre de documents trouvés
     */
    long countByExistsTrue();
    
    /**
     * Compte le nombre de documents non trouvés
     */
    long countByExistsFalse();
    
    /**
     * Récupère les URLs des documents trouvés
     */
    @Query("SELECT f.url FROM FetchResult f WHERE f.exists = true")
    List<String> findAllFoundUrls();
    
    /**
     * Récupère uniquement les documentIds (optimisé pour le cache)
     */
    @Query("SELECT f.documentId FROM FetchResult f")
    List<String> findAllDocumentIds();
    
    /**
     * Trouve les documents par statut et existence
     */
    @Query("SELECT f FROM FetchResult f WHERE f.status = :status AND f.exists = :exists")
    List<FetchResult> findByStatusAndExists(@org.springframework.data.repository.query.Param("status") String status, 
                                           @org.springframework.data.repository.query.Param("exists") Boolean exists);
    
    /**
     * Compte les documents par statut
     */
    long countByStatus(String status);
    
    /**
     * Compte les documents par documentId (pour tests d'idempotence)
     */
    long countByDocumentId(String documentId);
    
    /**
     * Compte les documents par type
     */
    long countByDocumentType(String documentType);
}
