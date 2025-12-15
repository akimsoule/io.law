package bj.gouv.sgg.repository;

import bj.gouv.sgg.model.FetchNotFoundRange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FetchNotFoundRangeRepository extends JpaRepository<FetchNotFoundRange, Long> {
    
    /**
     * Récupère toutes les plages pour un type et une année donnés, triées par numéro
     */
    List<FetchNotFoundRange> findByDocumentTypeAndYearOrderByNumberMinAsc(String documentType, Integer year);
    
    /**
     * Vérifie si un document est dans une plage NOT_FOUND
     */
    @Query("SELECT COUNT(r) > 0 FROM FetchNotFoundRange r WHERE r.documentType = :documentType AND r.year = :year " +
           "AND r.numberMin <= :number AND r.numberMax >= :number")
    boolean isInNotFoundRange(@Param("documentType") String documentType, 
                              @Param("year") Integer year, 
                              @Param("number") Integer number);
    
    /**
     * Récupère les plages qui peuvent chevaucher ou être adjacentes à un nouveau document
     */
    @Query("SELECT r FROM FetchNotFoundRange r WHERE r.documentType = :documentType AND r.year = :year " +
           "AND ((r.numberMin <= :number + 1 AND r.numberMax >= :number - 1))")
    List<FetchNotFoundRange> findOverlappingRanges(@Param("documentType") String documentType, 
                                                     @Param("year") Integer year, 
                                                     @Param("number") Integer number);
    
    /**
     * Vérifie si une plage exacte existe déjà (pour éviter les doublons)
     */
    @Query("SELECT COUNT(r) > 0 FROM FetchNotFoundRange r WHERE r.documentType = :documentType " +
           "AND r.year = :year AND r.numberMin = :numberMin AND r.numberMax = :numberMax")
    boolean existsExactRange(@Param("documentType") String documentType,
                             @Param("year") Integer year,
                             @Param("numberMin") Integer numberMin,
                             @Param("numberMax") Integer numberMax);
    
    /**
     * Récupère toutes les plages, triées par type, année et numéro
     */
    List<FetchNotFoundRange> findAllByOrderByDocumentTypeAscYearDescNumberMinAsc();
}
