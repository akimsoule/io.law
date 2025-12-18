package bj.gouv.sgg.repository;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository Spring Data JPA pour les documents légaux.
 * Spring génère automatiquement l'implémentation.
 */
@Repository
public interface LawDocumentRepository extends JpaRepository<LawDocumentEntity, Long> {
    
    /**
     * Trouve un document par son identifiant métier (ex: "loi-2024-15").
     * Méthode générée automatiquement par Spring Data JPA.
     */
    Optional<LawDocumentEntity> findByDocumentId(String documentId);
    
    /**
     * Trouve un document par type, année et numéro.
     * Méthode générée automatiquement par Spring Data JPA.
     */
    Optional<LawDocumentEntity> findByTypeAndYearAndNumber(String type, int year, String number);
    
    /**
     * Vérifie l'existence d'un document par son identifiant métier.
     * Méthode générée automatiquement par Spring Data JPA.
     */
    boolean existsByDocumentId(String documentId);
    
    /**
     * Vérifie l'existence d'un document par type, année et numéro.
     * Méthode générée automatiquement par Spring Data JPA.
     */
    boolean existsByTypeAndYearAndNumber(String type, int year, String number);
    
    /**
     * Trouve tous les documents avec un statut donné.
     * Méthode générée automatiquement par Spring Data JPA.
     */
    List<LawDocumentEntity> findByStatus(ProcessingStatus status);
    
    /**
     * Trouve tous les documents d'un type et année donnés.
     * Méthode générée automatiquement par Spring Data JPA.
     */
    List<LawDocumentEntity> findByTypeAndYear(String type, int year);
    
    /**
     * Trouve tous les documents fetchés d'un type sur une plage d'années.
     * Optimisé pour éviter les multiples requêtes en boucle.
     * Query personnalisée avec @Query.
     * 
     * @param type Type de document ("loi" ou "decret")
     * @param minYear Année minimale (inclusive)
     * @param maxYear Année maximale (inclusive)
     * @return Liste des documents avec status >= FETCHED sur la plage d'années
     */
    @Query("""
        SELECT d FROM LawDocumentEntity d 
        WHERE d.type = :type 
        AND d.year BETWEEN :minYear AND :maxYear
        AND d.status IN (
            bj.gouv.sgg.entity.ProcessingStatus.FETCHED,
            bj.gouv.sgg.entity.ProcessingStatus.DOWNLOADED,
            bj.gouv.sgg.entity.ProcessingStatus.OCRED,
            bj.gouv.sgg.entity.ProcessingStatus.EXTRACTED,
            bj.gouv.sgg.entity.ProcessingStatus.CONSOLIDATED
        )
        ORDER BY d.year DESC, d.number ASC
        """)
    List<LawDocumentEntity> findFetchedByTypeAndYearRange(
        @Param("type") String type, 
        @Param("minYear") int minYear, 
        @Param("maxYear") int maxYear
    );
    
    /**
     * Compte les documents avec un statut donné.
     * Méthode générée automatiquement par Spring Data JPA.
     */
    long countByStatus(ProcessingStatus status);
}
