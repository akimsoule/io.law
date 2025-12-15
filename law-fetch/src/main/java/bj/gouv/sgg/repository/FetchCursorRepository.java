package bj.gouv.sgg.repository;

import bj.gouv.sgg.model.FetchCursor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FetchCursorRepository extends JpaRepository<FetchCursor, Long> {
    
    /**
     * Trouve le cursor unique pour cursorType and fetchPrevious
     * Il n'y a qu'un seul cursor qui gère loi+decret ensemble
     */
    Optional<FetchCursor> findByCursorTypeAndDocumentType(String cursorType, String documentType);
    
    /**
     * Vérifie si le cursor existe
     */
    boolean existsByDocumentType(String documentType);
}
