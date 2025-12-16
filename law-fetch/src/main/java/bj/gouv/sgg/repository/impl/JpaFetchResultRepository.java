package bj.gouv.sgg.repository.impl;

import bj.gouv.sgg.entity.FetchResultEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * Implémentation JPA du repository pour les résultats de fetch.
 * Utilise EntityManager directement (pas Spring Data JPA).
 */
@Slf4j
@RequiredArgsConstructor
public class JpaFetchResultRepository {
    
    private final EntityManager entityManager;
    
    /**
     * Sauvegarde ou met à jour un résultat de fetch.
     */
    public FetchResultEntity save(FetchResultEntity result) {
        if (result.getId() == null) {
            entityManager.getTransaction().begin();
            try {
                entityManager.persist(result);
                entityManager.getTransaction().commit();
                log.debug("✅ FetchResult créé: {}", result.getDocumentId());
            } catch (Exception e) {
                if (entityManager.getTransaction().isActive()) {
                    entityManager.getTransaction().rollback();
                }
                log.error("❌ Erreur création FetchResult: {}", e.getMessage());
                throw new RuntimeException("Failed to create fetch result", e);
            }
        } else {
            entityManager.getTransaction().begin();
            try {
                FetchResultEntity merged = entityManager.merge(result);
                entityManager.getTransaction().commit();
                log.debug("✅ FetchResult mis à jour: {}", result.getDocumentId());
                return merged;
            } catch (Exception e) {
                if (entityManager.getTransaction().isActive()) {
                    entityManager.getTransaction().rollback();
                }
                log.error("❌ Erreur mise à jour FetchResult: {}", e.getMessage());
                throw new RuntimeException("Failed to update fetch result", e);
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
            "SELECT COUNT(f) FROM FetchResultEntity f WHERE f.documentId = :documentId",
            Long.class
        );
        query.setParameter("documentId", documentId);
        return query.getSingleResult() > 0;
    }
    
    /**
     * Trouve un résultat par documentId.
     */
    public Optional<FetchResultEntity> findByDocumentId(String documentId) {
        if (documentId == null || documentId.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            TypedQuery<FetchResultEntity> query = entityManager.createQuery(
                "SELECT f FROM FetchResultEntity f WHERE f.documentId = :documentId",
                FetchResultEntity.class
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
    public List<FetchResultEntity> findByType(String type) {
        if (type == null || type.isEmpty()) {
            return List.of();
        }
        
        TypedQuery<FetchResultEntity> query = entityManager.createQuery(
            "SELECT f FROM FetchResultEntity f WHERE f.type = :type",
            FetchResultEntity.class
        );
        query.setParameter("type", type);
        return query.getResultList();
    }
    
    /**
     * Trouve par type, année et numéro.
     */
    public Optional<FetchResultEntity> findByTypeAndYearAndNumber(String type, int year, int number) {
        if (type == null || type.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            TypedQuery<FetchResultEntity> query = entityManager.createQuery(
                "SELECT f FROM FetchResultEntity f WHERE f.type = :type AND f.year = :year AND f.number = :number",
                FetchResultEntity.class
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
     * Compte les fetch réussis (found=true).
     */
    public long countSuccessful() {
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(f) FROM FetchResultEntity f WHERE f.found = true",
            Long.class
        );
        return query.getSingleResult();
    }
    
    /**
     * Compte les fetch échoués (found=false).
     */
    public long countFailed() {
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(f) FROM FetchResultEntity f WHERE f.found = false",
            Long.class
        );
        return query.getSingleResult();
    }
    
    /**
     * Compte les résultats par année.
     */
    public long countByYear(int year) {
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(f) FROM FetchResultEntity f WHERE f.year = :year",
            Long.class
        );
        query.setParameter("year", year);
        return query.getSingleResult();
    }
    
    /**
     * Trouve tous les résultats.
     */
    public List<FetchResultEntity> findAll() {
        TypedQuery<FetchResultEntity> query = entityManager.createQuery(
            "SELECT f FROM FetchResultEntity f ORDER BY f.fetchedAt DESC",
            FetchResultEntity.class
        );
        return query.getResultList();
    }
    
    /**
     * Supprime tous les résultats (pour tests).
     */
    public void deleteAll() {
        entityManager.getTransaction().begin();
        try {
            int deleted = entityManager.createQuery("DELETE FROM FetchResultEntity").executeUpdate();
            entityManager.getTransaction().commit();
            log.debug("🗑️ {} FetchResult supprimés", deleted);
        } catch (Exception e) {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            throw new RuntimeException("Failed to delete all fetch results", e);
        }
    }
}
