package bj.gouv.sgg.repository;

import bj.gouv.sgg.model.FetchCursor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FetchCursorRepository extends JpaRepository<FetchCursor, Long> {
    
    /**
     * Trouve un cursor par type SEULEMENT (partagé entre lois et décrets)
     */
    Optional<FetchCursor> findByCursorType(String cursorType);
    
    /**
     * Trouve un cursor par type et documentType
     */
    Optional<FetchCursor> findByCursorTypeAndDocumentType(String cursorType, String documentType);
    
    /**
     * Vérifie si un cursor existe
     */
    boolean existsByCursorTypeAndDocumentType(String cursorType, String documentType);
}
