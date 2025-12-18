package bj.gouv.sgg.repository;

import bj.gouv.sgg.entity.FetchCursorEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository Spring Data JPA pour les cursors de fetch.
 * Remplace JpaFetchCursorRepository manuel.
 */
@Repository
public interface FetchCursorRepository extends JpaRepository<FetchCursorEntity, Long> {
    
    /**
     * Trouve le cursor unique pour un type de cursor et un type de document.
     * Utilise un verrou pessimiste pour éviter les duplications en multi-threading.
     * 
     * @param cursorType Type de cursor (current ou previous)
     * @param documentType Type de document (loi ou decret)
     * @return Le cursor s'il existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM FetchCursorEntity c WHERE c.cursorType = :cursorType AND c.documentType = :documentType")
    Optional<FetchCursorEntity> findByCursorTypeAndDocumentType(
        @Param("cursorType") String cursorType, 
        @Param("documentType") String documentType
    );
    
    /**
     * Version sans verrou pour les tests et lectures hors transaction.
     * Ne pas utiliser dans le code métier multi-threadé.
     */
    @Query("SELECT c FROM FetchCursorEntity c WHERE c.cursorType = :cursorType AND c.documentType = :documentType")
    Optional<FetchCursorEntity> findByCursorTypeAndDocumentTypeNoLock(
        @Param("cursorType") String cursorType, 
        @Param("documentType") String documentType
    );
}
