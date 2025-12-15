package bj.gouv.sgg.repository;

import bj.gouv.sgg.model.FetchNotFound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository pour les documents NOT_FOUND
 */
@Repository
public interface FetchNotFoundRepository extends JpaRepository<FetchNotFound, Long> {
    
    /**
     * Vérifie si un document est marqué comme NOT_FOUND
     */
    boolean existsByDocumentTypeAndYearAndNumber(String documentType, Integer year, Integer number);
    
    /**
     * Compte le nombre de documents NOT_FOUND pour un type et une année
     */
    @Query("SELECT COUNT(f) FROM FetchNotFound f WHERE f.documentType = :type AND f.year = :year")
    long countByTypeAndYear(@Param("type") String type, @Param("year") Integer year);
}
