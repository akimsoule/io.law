package bj.gouv.sgg.repository.impl;

import bj.gouv.sgg.entity.NotFoundRecordEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * Implémentation JPA du repository pour les documents non trouvés (404).
 * Utilise EntityManager directement (pas Spring Data JPA).
 */
@Slf4j
@RequiredArgsConstructor
public class JpaNotFoundRecordRepository {
    
    private final EntityManager entityManager;
    
    /**
     * Sauvegarde ou met à jour un enregistrement not found.
     */
    public NotFoundRecordEntity save(NotFoundRecordEntity record) {
        if (record.getId() == null) {
            entityManager.getTransaction().begin();
            try {
                entityManager.persist(record);
                entityManager.getTransaction().commit();
                log.debug("✅ NotFoundRecord créé: {}", record.getDocumentId());
            } catch (Exception e) {
                if (entityManager.getTransaction().isActive()) {
                    entityManager.getTransaction().rollback();
                }
                log.error("❌ Erreur création NotFoundRecord: {}", e.getMessage());
                throw new RuntimeException("Failed to create not found record", e);
            }
        } else {
            entityManager.getTransaction().begin();
            try {
                NotFoundRecordEntity merged = entityManager.merge(record);
                entityManager.getTransaction().commit();
                log.debug("✅ NotFoundRecord mis à jour: {}", record.getDocumentId());
                return merged;
            } catch (Exception e) {
                if (entityManager.getTransaction().isActive()) {
                    entityManager.getTransaction().rollback();
                }
                log.error("❌ Erreur mise à jour NotFoundRecord: {}", e.getMessage());
                throw new RuntimeException("Failed to update not found record", e);
            }
        }
        return record;
    }
    
    /**
     * Trouve un enregistrement par documentId.
     */
    public Optional<NotFoundRecordEntity> findByDocumentId(String documentId) {
        if (documentId == null || documentId.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            TypedQuery<NotFoundRecordEntity> query = entityManager.createQuery(
                "SELECT n FROM NotFoundRecordEntity n WHERE n.documentId = :documentId",
                NotFoundRecordEntity.class
            );
            query.setParameter("documentId", documentId);
            return Optional.of(query.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Vérifie si un enregistrement existe.
     */
    public boolean existsByDocumentId(String documentId) {
        if (documentId == null || documentId.isEmpty()) {
            return false;
        }
        
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(n) FROM NotFoundRecordEntity n WHERE n.documentId = :documentId",
            Long.class
        );
        query.setParameter("documentId", documentId);
        return query.getSingleResult() > 0;
    }
    
    /**
     * Vérifie si un enregistrement existe par type/year/number.
     */
    public boolean existsByTypeAndYearAndNumber(String type, int year, int number) {
        if (type == null || type.isEmpty()) {
            return false;
        }
        
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(n) FROM NotFoundRecordEntity n WHERE n.type = :type AND n.year = :year AND n.number = :number",
            Long.class
        );
        query.setParameter("type", type);
        query.setParameter("year", year);
        query.setParameter("number", number);
        return query.getSingleResult() > 0;
    }
    
    /**
     * Trouve par type, année et numéro.
     */
    public Optional<NotFoundRecordEntity> findByTypeAndYearAndNumber(String type, int year, int number) {
        if (type == null || type.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            TypedQuery<NotFoundRecordEntity> query = entityManager.createQuery(
                "SELECT n FROM NotFoundRecordEntity n WHERE n.type = :type AND n.year = :year AND n.number = :number",
                NotFoundRecordEntity.class
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
     * Vérifie si un document est marqué not found.
     */
    public boolean isNotFound(String type, int year, int number) {
        if (type == null || type.isEmpty()) {
            return false;
        }
        
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(n) FROM NotFoundRecordEntity n WHERE n.type = :type AND n.year = :year AND n.number = :number",
            Long.class
        );
        query.setParameter("type", type);
        query.setParameter("year", year);
        query.setParameter("number", number);
        return query.getSingleResult() > 0;
    }
    
    /**
     * Trouve tous les enregistrements not found pour un type.
     */
    public List<NotFoundRecordEntity> findByType(String type) {
        if (type == null || type.isEmpty()) {
            return List.of();
        }
        
        TypedQuery<NotFoundRecordEntity> query = entityManager.createQuery(
            "SELECT n FROM NotFoundRecordEntity n WHERE n.type = :type ORDER BY n.year DESC, n.number DESC",
            NotFoundRecordEntity.class
        );
        query.setParameter("type", type);
        return query.getResultList();
    }
    
    /**
     * Trouve tous les enregistrements not found pour une année.
     */
    public List<NotFoundRecordEntity> findByYear(int year) {
        TypedQuery<NotFoundRecordEntity> query = entityManager.createQuery(
            "SELECT n FROM NotFoundRecordEntity n WHERE n.year = :year ORDER BY n.type, n.number",
            NotFoundRecordEntity.class
        );
        query.setParameter("year", year);
        return query.getResultList();
    }
    
    /**
     * Compte les not found par type.
     */
    public long countByType(String type) {
        if (type == null || type.isEmpty()) {
            return 0;
        }
        
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(n) FROM NotFoundRecordEntity n WHERE n.type = :type",
            Long.class
        );
        query.setParameter("type", type);
        return query.getSingleResult();
    }
    
    /**
     * Trouve tous les enregistrements.
     */
    public List<NotFoundRecordEntity> findAll() {
        TypedQuery<NotFoundRecordEntity> query = entityManager.createQuery(
            "SELECT n FROM NotFoundRecordEntity n ORDER BY n.checkedAt DESC",
            NotFoundRecordEntity.class
        );
        return query.getResultList();
    }
    
    /**
     * Supprime tous les enregistrements (pour tests).
     */
    public void deleteAll() {
        entityManager.getTransaction().begin();
        try {
            int deleted = entityManager.createQuery("DELETE FROM NotFoundRecordEntity").executeUpdate();
            entityManager.getTransaction().commit();
            log.debug("🗑️ {} NotFoundRecord supprimés", deleted);
        } catch (Exception e) {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            throw new RuntimeException("Failed to delete all not found records", e);
        }
    }
}
