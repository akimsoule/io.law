package bj.gouv.sgg.consolidate.repository;

import bj.gouv.sgg.consolidate.model.ConsolidatedSignatory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository pour l'entité ConsolidatedSignatory.
 * Gère les opérations CRUD sur les signataires consolidés.
 * 
 * <p><b>Idempotence</b> : Les opérations sont idempotentes grâce à la contrainte
 * {@code UNIQUE(documentId, signatoryOrder)}. Un signataire à une position donnée
 * n'est inséré qu'une seule fois.
 * 
 * @see ConsolidatedSignatory
 */
@Repository
public interface ConsolidatedSignatoryRepository extends JpaRepository<ConsolidatedSignatory, Long> {
    
    /**
     * Trouve tous les signataires d'un document, triés par ordre d'apparition.
     * 
     * @param documentId ID du document (ex: "loi-2024-15")
     * @return Liste des signataires triés par signatoryOrder ASC
     */
    List<ConsolidatedSignatory> findByDocumentIdOrderBySignatoryOrderAsc(String documentId);
    
    /**
     * Trouve un signataire spécifique d'un document.
     * 
     * @param documentId ID du document (ex: "loi-2024-15")
     * @param signatoryOrder Ordre du signataire (1, 2, 3...)
     * @return Signataire si trouvé, sinon Optional.empty()
     */
    Optional<ConsolidatedSignatory> findByDocumentIdAndSignatoryOrder(String documentId, Integer signatoryOrder);
    
    /**
     * Trouve tous les signataires par rôle (ex: "Président de la République").
     * 
     * @param role Rôle du signataire
     * @return Liste des signataires ayant ce rôle
     */
    List<ConsolidatedSignatory> findByRole(String role);
    
    /**
     * Trouve tous les signataires par nom (ex: "Patrice TALON").
     * 
     * @param name Nom du signataire
     * @return Liste des signataires ayant ce nom
     */
    List<ConsolidatedSignatory> findByName(String name);
    
    /**
     * Trouve tous les signataires d'un type de document (loi ou decret).
     * 
     * @param documentType Type de document (loi, decret)
     * @return Liste des signataires du type donné
     */
    List<ConsolidatedSignatory> findByDocumentType(String documentType);
    
    /**
     * Trouve tous les signataires d'une année donnée.
     * 
     * @param year Année du document (ex: 2024)
     * @return Liste des signataires de l'année donnée
     */
    List<ConsolidatedSignatory> findByDocumentYear(Integer year);
    
    /**
     * Compte le nombre de signataires d'un document.
     * 
     * @param documentId ID du document (ex: "loi-2024-15")
     * @return Nombre de signataires
     */
    long countByDocumentId(String documentId);
    
    /**
     * Vérifie si un signataire existe déjà.
     * 
     * @param documentId ID du document (ex: "loi-2024-15")
     * @param signatoryOrder Ordre du signataire (1, 2, 3...)
     * @return true si le signataire existe, false sinon
     */
    boolean existsByDocumentIdAndSignatoryOrder(String documentId, Integer signatoryOrder);
    
    /**
     * Supprime tous les signataires d'un document.
     * Utile pour re-consolidation complète.
     * 
     * @param documentId ID du document (ex: "loi-2024-15")
     */
    void deleteByDocumentId(String documentId);
    
    /**
     * Compte le nombre total de signataires consolidés.
     * 
     * @return Nombre total de signataires en base
     */
    @Query("SELECT COUNT(s) FROM ConsolidatedSignatory s")
    long countAllSignatories();
    
    /**
     * Trouve tous les signataires distincts (nom + rôle unique).
     * 
     * @return Liste des signataires distincts
     */
    @Query("SELECT DISTINCT s.name, s.role FROM ConsolidatedSignatory s ORDER BY s.name")
    List<Object[]> findDistinctSignatories();
}
