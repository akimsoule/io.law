package bj.gouv.sgg.repository.impl;

import bj.gouv.sgg.entity.DownloadResultEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * Implémentation JPA du repository pour les résultats de téléchargement.
 * Utilise EntityManager directement (pas Spring Data JPA).
 */
@Slf4j
@RequiredArgsConstructor
public class JpaDownloadResultRepository {
    
    private final EntityManager entityManager;
    
    /**
     * Sauvegarde ou met à jour un résultat de téléchargement.
     */
    public DownloadResultEntity save(DownloadResultEntity result) {
        if (result.getId() == null) {
            entityManager.getTransaction().begin();
            try {
                entityManager.persist(result);
                entityManager.getTransaction().commit();
                log.debug("✅ DownloadResult créé: {}", result.getDocumentId());
            } catch (Exception e) {
                if (entityManager.getTransaction().isActive()) {
                    entityManager.getTransaction().rollback();
                }
                log.error("❌ Erreur création DownloadResult: {}", e.getMessage());
                throw new RuntimeException("Failed to create download result", e);
            }
        } else {
            entityManager.getTransaction().begin();
            try {
                DownloadResultEntity merged = entityManager.merge(result);
                entityManager.getTransaction().commit();
                log.debug("✅ DownloadResult mis à jour: {}", result.getDocumentId());
                return merged;
            } catch (Exception e) {
                if (entityManager.getTransaction().isActive()) {
                    entityManager.getTransaction().rollback();
                }
                log.error("❌ Erreur mise à jour DownloadResult: {}", e.getMessage());
                throw new RuntimeException("Failed to update download result", e);
            }
        }
        return result;
    }
    
    /**
     * Vérifie si un résultat existe par documentId.
     */
    public boolean existsByDocumentId(String documentId) {
        if (documentId == null || documentId.isEmpty()) {
            return false;
        }
        
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(d) FROM DownloadResultEntity d WHERE d.documentId = :documentId",
            Long.class
        );
        query.setParameter("documentId", documentId);
        return query.getSingleResult() > 0;
    }
    
    /**
     * Trouve un résultat par documentId.
     */
    public Optional<DownloadResultEntity> findByDocumentId(String documentId) {
        if (documentId == null || documentId.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            TypedQuery<DownloadResultEntity> query = entityManager.createQuery(
                "SELECT d FROM DownloadResultEntity d WHERE d.documentId = :documentId",
                DownloadResultEntity.class
            );
            query.setParameter("documentId", documentId);
            return Optional.of(query.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Trouve tous les résultats pour un type.
     */
    public List<DownloadResultEntity> findByType(String type) {
        if (type == null || type.isEmpty()) {
            return List.of();
        }
        
        TypedQuery<DownloadResultEntity> query = entityManager.createQuery(
            "SELECT d FROM DownloadResultEntity d WHERE d.type = :type",
            DownloadResultEntity.class
        );
        query.setParameter("type", type);
        return query.getResultList();
    }
    
    /**
     * Trouve par type, année et numéro.
     */
    public Optional<DownloadResultEntity> findByTypeAndYearAndNumber(String type, int year, int number) {
        if (type == null || type.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            TypedQuery<DownloadResultEntity> query = entityManager.createQuery(
                "SELECT d FROM DownloadResultEntity d WHERE d.type = :type AND d.year = :year AND d.number = :number",
                DownloadResultEntity.class
            );
            query.setParameter("type", type);
            query.setParameter("year", year);
            query.setParameter("number", number);
            return Optional.of(query.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Compte les téléchargements réussis.
     */
    public long countSuccessful() {
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(d) FROM DownloadResultEntity d WHERE d.success = true",
            Long.class
        );
        return query.getSingleResult();
    }
    
    /**
     * Compte les téléchargements échoués.
     */
    public long countFailed() {
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(d) FROM DownloadResultEntity d WHERE d.success = false",
            Long.class
        );
        return query.getSingleResult();
    }
    
    /**
     * Calcule la taille totale téléchargée (en bytes).
     */
    public long totalBytesDownloaded() {
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COALESCE(SUM(d.fileSize), 0) FROM DownloadResultEntity d WHERE d.success = true",
            Long.class
        );
        return query.getSingleResult();
    }
    
    /**
     * Trouve tous les résultats.
     */
    public List<DownloadResultEntity> findAll() {
        TypedQuery<DownloadResultEntity> query = entityManager.createQuery(
            "SELECT d FROM DownloadResultEntity d ORDER BY d.downloadedAt DESC",
            DownloadResultEntity.class
        );
        return query.getResultList();
    }
    
    /**
     * Trouve les résultats avec SHA256 hash spécifique (détection doublons).
     */
    public List<DownloadResultEntity> findBySha256Hash(String sha256Hash) {
        if (sha256Hash == null || sha256Hash.isEmpty()) {
            return List.of();
        }
        
        TypedQuery<DownloadResultEntity> query = entityManager.createQuery(
            "SELECT d FROM DownloadResultEntity d WHERE d.sha256Hash = :hash",
            DownloadResultEntity.class
        );
        query.setParameter("hash", sha256Hash);
        return query.getResultList();
    }
    
    /**
     * Supprime tous les résultats (pour tests).
     */
    public void deleteAll() {
        entityManager.getTransaction().begin();
        try {
            int deleted = entityManager.createQuery("DELETE FROM DownloadResultEntity").executeUpdate();
            entityManager.getTransaction().commit();
            log.debug("🗑️ {} DownloadResult supprimés", deleted);
        } catch (Exception e) {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            throw new RuntimeException("Failed to delete all download results", e);
        }
    }
}
