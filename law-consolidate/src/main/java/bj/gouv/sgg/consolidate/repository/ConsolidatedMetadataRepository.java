package bj.gouv.sgg.consolidate.repository;

import bj.gouv.sgg.consolidate.model.ConsolidatedMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository pour l'entité ConsolidatedMetadata.
 * Gère les opérations CRUD sur les métadonnées consolidées.
 * 
 * <p><b>Idempotence</b> : Les opérations sont idempotentes grâce à la contrainte
 * {@code UNIQUE(documentId)}. Un document n'a qu'une seule entrée de metadata.
 * 
 * @see ConsolidatedMetadata
 */
@Repository
public interface ConsolidatedMetadataRepository extends JpaRepository<ConsolidatedMetadata, Long> {
    
    /**
     * Trouve les métadonnées d'un document par son ID.
     * 
     * @param documentId ID du document (ex: "loi-2024-15")
     * @return Métadonnées si trouvées, sinon Optional.empty()
     */
    Optional<ConsolidatedMetadata> findByDocumentId(String documentId);
    
    /**
     * Trouve toutes les métadonnées d'un type de document.
     * 
     * @param documentType Type de document (loi, decret)
     * @return Liste des métadonnées du type donné, triées par année DESC, numéro DESC
     */
    List<ConsolidatedMetadata> findByDocumentTypeOrderByDocumentYearDescDocumentNumberDesc(String documentType);
    
    /**
     * Trouve toutes les métadonnées d'une année donnée.
     * 
     * @param year Année du document (ex: 2024)
     * @return Liste des métadonnées de l'année donnée, triées par numéro DESC
     */
    List<ConsolidatedMetadata> findByDocumentYearOrderByDocumentNumberDesc(Integer year);
    
    /**
     * Trouve toutes les métadonnées d'un type et d'une année.
     * 
     * @param documentType Type de document (loi, decret)
     * @param year Année du document (ex: 2024)
     * @return Liste des métadonnées, triées par numéro DESC
     */
    List<ConsolidatedMetadata> findByDocumentTypeAndDocumentYearOrderByDocumentNumberDesc(
        String documentType, Integer year
    );
    
    /**
     * Trouve toutes les métadonnées avec une confiance >= seuil donné.
     * 
     * @param minConfidence Confiance minimum (0.0 à 1.0)
     * @return Liste des métadonnées avec confiance >= minConfidence
     */
    @Query("SELECT m FROM ConsolidatedMetadata m WHERE m.extractionConfidence >= :minConfidence ORDER BY m.documentYear DESC, m.documentNumber DESC")
    List<ConsolidatedMetadata> findHighConfidenceMetadata(@Param("minConfidence") Double minConfidence);
    
    /**
     * Vérifie si les métadonnées d'un document existent.
     * 
     * @param documentId ID du document (ex: "loi-2024-15")
     * @return true si les métadonnées existent, false sinon
     */
    boolean existsByDocumentId(String documentId);
    
    /**
     * Supprime les métadonnées d'un document.
     * 
     * @param documentId ID du document (ex: "loi-2024-15")
     */
    void deleteByDocumentId(String documentId);
    
    /**
     * Compte le nombre total de documents consolidés.
     * 
     * @return Nombre total de documents en base
     */
    @Query("SELECT COUNT(m) FROM ConsolidatedMetadata m")
    long countAllDocuments();
    
    /**
     * Compte les documents par type.
     * 
     * @param documentType Type de document (loi, decret)
     * @return Nombre de documents du type donné
     */
    long countByDocumentType(String documentType);
    
    /**
     * Compte les documents d'une année.
     * 
     * @param year Année du document (ex: 2024)
     * @return Nombre de documents de l'année donnée
     */
    long countByDocumentYear(Integer year);
}
