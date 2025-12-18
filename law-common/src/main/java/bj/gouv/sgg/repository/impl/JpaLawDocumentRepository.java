package bj.gouv.sgg.repository.impl;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.repository.LawDocumentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * Implémentation JPA du repository pour les documents légaux.
 * Utilise EntityManager directement (pas Spring Data JPA).
 */
@Slf4j
@RequiredArgsConstructor
public class JpaLawDocumentRepository implements LawDocumentRepository {
    
    private final EntityManager entityManager;
    
    @Override
    public Optional<LawDocumentEntity> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        LawDocumentEntity entity = entityManager.find(LawDocumentEntity.class, id);
        return Optional.ofNullable(entity);
    }
    
    @Override
    public Optional<LawDocumentEntity> findByDocumentId(String documentId) {
        if (documentId == null || documentId.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            TypedQuery<LawDocumentEntity> query = entityManager.createQuery(
                "SELECT d FROM LawDocumentEntity d WHERE d.documentId = :documentId",
                LawDocumentEntity.class
            );
            query.setParameter("documentId", documentId);
            return Optional.of(query.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<LawDocumentEntity> findByTypeAndYearAndNumber(String type, int year, String number) {
        if (type == null || type.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            TypedQuery<LawDocumentEntity> query = entityManager.createQuery(
                "SELECT d FROM LawDocumentEntity d WHERE d.type = :type AND d.year = :year AND d.number = :number",
                LawDocumentEntity.class
            );
            query.setParameter("type", type);
            query.setParameter("year", year);
            query.setParameter("number", number);
            return Optional.of(query.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
    
    @Override
    public boolean existsByDocumentId(String documentId) {
        if (documentId == null || documentId.isEmpty()) {
            return false;
        }
        
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(d) FROM LawDocumentEntity d WHERE d.documentId = :documentId",
            Long.class
        );
        query.setParameter("documentId", documentId);
        return query.getSingleResult() > 0;
    }
    
    @Override
    public boolean existsByTypeAndYearAndNumber(String type, int year, int number) {
        if (type == null || type.isEmpty()) {
            return false;
        }
        
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(d) FROM LawDocumentEntity d WHERE d.type = :type AND d.year = :year AND d.number = :number",
            Long.class
        );
        query.setParameter("type", type);
        query.setParameter("year", year);
        query.setParameter("number", number);
        return query.getSingleResult() > 0;
    }
    
    @Override
    public List<LawDocumentEntity> findByStatus(ProcessingStatus status) {
        if (status == null) {
            return List.of();
        }
        
        TypedQuery<LawDocumentEntity> query = entityManager.createQuery(
            "SELECT d FROM LawDocumentEntity d WHERE d.status = :status",
            LawDocumentEntity.class
        );
        query.setParameter("status", status);
        return query.getResultList();
    }
    
    @Override
    public List<LawDocumentEntity> findByTypeAndYear(String type, int year) {
        if (type == null || type.isEmpty()) {
            return List.of();
        }
        
        TypedQuery<LawDocumentEntity> query = entityManager.createQuery(
            "SELECT d FROM LawDocumentEntity d WHERE d.type = :type AND d.year = :year ORDER BY d.number",
            LawDocumentEntity.class
        );
        query.setParameter("type", type);
        query.setParameter("year", year);
        return query.getResultList();
    }
    
    @Override
    public List<LawDocumentEntity> findFetchedByTypeAndYearRange(String type, int minYear, int maxYear) {
        if (type == null || type.isEmpty()) {
            return List.of();
        }
        
        TypedQuery<LawDocumentEntity> query = entityManager.createQuery(
            "SELECT d FROM LawDocumentEntity d " +
            "WHERE d.type = :type " +
            "AND d.year BETWEEN :minYear AND :maxYear " +
            "AND d.status IN (" +
            "    bj.gouv.sgg.entity.ProcessingStatus.FETCHED, " +
            "    bj.gouv.sgg.entity.ProcessingStatus.DOWNLOADED, " +
            "    bj.gouv.sgg.entity.ProcessingStatus.OCRED, " +
            "    bj.gouv.sgg.entity.ProcessingStatus.EXTRACTED, " +
            "    bj.gouv.sgg.entity.ProcessingStatus.CONSOLIDATED" +
            ") " +
            "ORDER BY d.year DESC, d.number ASC",
            LawDocumentEntity.class
        );
        query.setParameter("type", type);
        query.setParameter("minYear", minYear);
        query.setParameter("maxYear", maxYear);
        return query.getResultList();
    }
    
    @Override
    public long countByStatus(ProcessingStatus status) {
        if (status == null) {
            return 0;
        }
        
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(d) FROM LawDocumentEntity d WHERE d.status = :status",
            Long.class
        );
        query.setParameter("status", status);
        return query.getSingleResult();
    }
    
    @Override
    public List<LawDocumentEntity> findAll() {
        TypedQuery<LawDocumentEntity> query = entityManager.createQuery(
            "SELECT d FROM LawDocumentEntity d ORDER BY d.year DESC, d.number ASC",
            LawDocumentEntity.class
        );
        return query.getResultList();
    }
    
    @Override
    public LawDocumentEntity save(LawDocumentEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        
        boolean shouldManageTransaction = !entityManager.getTransaction().isActive();
        
        try {
            if (shouldManageTransaction) {
                entityManager.getTransaction().begin();
            }
            
            if (entity.getId() == null) {
                // Insert
                entityManager.persist(entity);
                log.debug("✅ Inserted document: {}", entity.getDocumentId());
            } else {
                // Update
                entity = entityManager.merge(entity);
                log.debug("✅ Updated document: {}", entity.getDocumentId());
            }
            
            if (shouldManageTransaction) {
                entityManager.getTransaction().commit();
            }
            return entity;
            
        } catch (Exception e) {
            if (shouldManageTransaction && entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            log.error("❌ Failed to save document: {}", entity.getDocumentId(), e);
            throw new PersistenceException("Failed to save entity: " + entity.getDocumentId(), e);
        }
    }
    
    @Override
    public List<LawDocumentEntity> saveAll(List<LawDocumentEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        
        boolean shouldManageTransaction = !entityManager.getTransaction().isActive();
        
        try {
            if (shouldManageTransaction) {
                entityManager.getTransaction().begin();
            }
            
            for (LawDocumentEntity entity : entities) {
                if (entity.getId() == null) {
                    entityManager.persist(entity);
                } else {
                    entityManager.merge(entity);
                }
            }
            
            if (shouldManageTransaction) {
                entityManager.getTransaction().commit();
            }
            log.debug("✅ Saved {} documents", entities.size());
            return entities;
            
        } catch (Exception e) {
            if (shouldManageTransaction && entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            log.error("❌ Failed to save {} documents", entities.size(), e);
            throw new PersistenceException("Failed to save " + entities.size() + " entities", e);
        }
    }
    
    @Override
    public void delete(LawDocumentEntity entity) {
        if (entity == null) {
            return;
        }
        
        boolean shouldManageTransaction = !entityManager.getTransaction().isActive();
        
        try {
            if (shouldManageTransaction) {
                entityManager.getTransaction().begin();
            }
            
            if (entityManager.contains(entity)) {
                entityManager.remove(entity);
            } else {
                entityManager.remove(entityManager.merge(entity));
            }
            
            if (shouldManageTransaction) {
                entityManager.getTransaction().commit();
            }
            log.debug("✅ Deleted document: {}", entity.getDocumentId());
            
        } catch (Exception e) {
            if (shouldManageTransaction && entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            log.error("❌ Failed to delete document: {}", entity.getDocumentId(), e);
            throw new PersistenceException("Failed to delete entity: " + entity.getDocumentId(), e);
        }
    }
    
    @Override
    public void deleteAll() {
        boolean shouldManageTransaction = !entityManager.getTransaction().isActive();
        
        try {
            if (shouldManageTransaction) {
                entityManager.getTransaction().begin();
            }
            
            int count = entityManager.createQuery("DELETE FROM LawDocumentEntity")
                .executeUpdate();
            
            if (shouldManageTransaction) {
                entityManager.getTransaction().commit();
            }
            log.warn("⚠️ Deleted all {} documents", count);
            
        } catch (Exception e) {
            if (shouldManageTransaction && entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            log.error("❌ Failed to delete all documents", e);
            throw new PersistenceException("Failed to delete all entities", e);
        }
    }
}
