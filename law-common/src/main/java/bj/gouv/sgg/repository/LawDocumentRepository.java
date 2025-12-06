package bj.gouv.sgg.repository;

import bj.gouv.sgg.model.LawDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository pour l'entité LawDocument.
 * Gère les opérations CRUD sur les documents de loi.
 */
@Repository
public interface LawDocumentRepository extends JpaRepository<LawDocument, Long> {
    
    /**
     * Trouve un document par type, année et numéro.
     */
    Optional<LawDocument> findByTypeAndYearAndNumber(String type, int year, int number);
    
    /**
     * Trouve tous les documents avec un statut donné.
     */
    List<LawDocument> findByStatus(LawDocument.ProcessingStatus status);
    
    /**
     * Trouve tous les documents d'un type donné (loi ou decret).
     */
    List<LawDocument> findByType(String type);
    
    /**
     * Trouve tous les documents d'une année donnée.
     */
    List<LawDocument> findByYear(int year);
    
    /**
     * Trouve les documents par type et année, triés par numéro décroissant.
     */
    List<LawDocument> findByTypeAndYearOrderByNumberDesc(String type, int year);
    
    /**
     * Compte les documents avec un statut donné.
     */
    long countByStatus(LawDocument.ProcessingStatus status);
    
    /**
     * Vérifie si un document existe par type, année et numéro.
     */
    boolean existsByTypeAndYearAndNumber(String type, int year, int number);
    
    /**
     * Trouve les documents par type, année et statut.
     */
    @Query("SELECT d FROM LawDocument d WHERE d.type = :type AND d.year = :year AND d.status = :status")
    List<LawDocument> findByTypeYearAndStatus(@Param("type") String type, 
                                                @Param("year") int year, 
                                                @Param("status") LawDocument.ProcessingStatus status);
}
