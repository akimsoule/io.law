package bj.gouv.sgg.repository.impl;

import bj.gouv.sgg.entity.FetchCursorEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * Implémentation JPA du repository pour les cursors de fetch.
 * Utilise EntityManager directement (pas Spring Data JPA).
 */
@Slf4j
@RequiredArgsConstructor
public class JpaFetchCursorRepository {
    
    private final EntityManager entityManager;
    private static final String PARAM_DOCUMENT_TYPE = "documentType";
    
    /**
     * Trouve le cursor unique pour cursorType et documentType.
     */
    public Optional<FetchCursorEntity> findByCursorTypeAndDocumentType(String cursorType, String documentType) {
        if (cursorType == null || cursorType.isEmpty() || documentType == null || documentType.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            TypedQuery<FetchCursorEntity> query = entityManager.createQuery(
                "SELECT c FROM FetchCursorEntity c WHERE c.cursorType = :cursorType AND c.documentType = :documentType",
                FetchCursorEntity.class
            );
            query.setParameter("cursorType", cursorType);
            query.setParameter(PARAM_DOCUMENT_TYPE, documentType);
            return Optional.of(query.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Vérifie si un cursor existe par type de document.
     */
    public boolean existsByDocumentType(String documentType) {
        if (documentType == null || documentType.isEmpty()) {
            return false;
        }
        
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(c) FROM FetchCursorEntity c WHERE c.documentType = :documentType",
            Long.class
        );
        query.setParameter(PARAM_DOCUMENT_TYPE, documentType);
        return query.getSingleResult() > 0;
    }
    
    /**
     * Vérifie si un cursor existe par type de cursor et type de document.
     */
    public boolean existsByCursorTypeAndDocumentType(String cursorType, String documentType) {
        if (cursorType == null || cursorType.isEmpty() || documentType == null || documentType.isEmpty()) {
            return false;
        }
        
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(c) FROM FetchCursorEntity c WHERE c.cursorType = :cursorType AND c.documentType = :documentType",
            Long.class
        );
        query.setParameter("cursorType", cursorType);
        query.setParameter(PARAM_DOCUMENT_TYPE, documentType);
        return query.getSingleResult() > 0;
    }
    
    /**
     * Sauvegarde ou met à jour un cursor.
     */
    public FetchCursorEntity save(FetchCursorEntity cursor) {
        boolean shouldManageTransaction = !entityManager.getTransaction().isActive();
        
        try {
            if (shouldManageTransaction) {
                entityManager.getTransaction().begin();
            }
            
            // Chercher cursor existant
            Optional<FetchCursorEntity> existing = findByCursorTypeAndDocumentType(
                cursor.getCursorType(), 
                cursor.getDocumentType()
            );
            
            FetchCursorEntity result;
            if (existing.isPresent()) {
                // Mise à jour
                FetchCursorEntity toUpdate = existing.get();
                toUpdate.setCurrentYear(cursor.getCurrentYear());
                toUpdate.setCurrentNumber(cursor.getCurrentNumber());
                result = entityManager.merge(toUpdate);
                log.debug("✅ Cursor mis à jour: {}-{}", cursor.getCursorType(), cursor.getDocumentType());
            } else {
                // Création
                entityManager.persist(cursor);
                result = cursor;
                log.debug("✅ Cursor créé: {}-{}", cursor.getCursorType(), cursor.getDocumentType());
            }
            
            if (shouldManageTransaction) {
                entityManager.getTransaction().commit();
            }
            return result;
            
        } catch (Exception e) {
            if (shouldManageTransaction && entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            log.error("❌ Erreur sauvegarde cursor: {}", e.getMessage());
            throw new PersistenceException("Failed to save cursor: " + cursor.getCursorType() + "-" + cursor.getDocumentType(), e);
        }
    }
    
    /**
     * Récupère tous les cursors.
     */
    public List<FetchCursorEntity> findAll() {
        TypedQuery<FetchCursorEntity> query = entityManager.createQuery(
            "SELECT c FROM FetchCursorEntity c ORDER BY c.updatedAt DESC",
            FetchCursorEntity.class
        );
        return query.getResultList();
    }
    
    /**
     * Supprime tous les cursors (pour tests).
     */
    public void deleteAll() {
        boolean shouldManageTransaction = !entityManager.getTransaction().isActive();
        
        try {
            if (shouldManageTransaction) {
                entityManager.getTransaction().begin();
            }
            
            int deleted = entityManager.createQuery("DELETE FROM FetchCursorEntity").executeUpdate();
            log.debug("🗑️ {} Cursors supprimés", deleted);
            
            if (shouldManageTransaction) {
                entityManager.getTransaction().commit();
            }
            
        } catch (Exception e) {
            if (shouldManageTransaction && entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            throw new PersistenceException("Failed to delete all cursors", e);
        }
    }
}
