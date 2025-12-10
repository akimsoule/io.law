package bj.gouv.sgg.consolidate.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entité représentant un signataire consolidé d'un document légal.
 * 
 * <p>Cette table stocke les signataires extraits depuis les fichiers JSON :
 * Président, Ministre, Garde des Sceaux, etc.
 * 
 * <p><b>Schéma</b> :
 * <ul>
 *   <li>PK : {@code id} (auto-increment)</li>
 *   <li>UK : {@code documentId + signatoryOrder} (plusieurs signataires par document)</li>
 *   <li>Index : {@code documentId}, {@code role}, {@code name}</li>
 * </ul>
 * 
 * <p><b>Idempotence</b> : La contrainte {@code UNIQUE(documentId, signatoryOrder)}
 * garantit qu'un signataire n'est inséré qu'une seule fois à sa position.
 * En cas de re-run, l'UPDATE remplace l'ancienne version.
 * 
 * @see bj.gouv.sgg.consolidate.model.ConsolidatedMetadata
 */
@Entity
@Table(
    name = "consolidated_signatories",
    indexes = {
        @Index(name = "idx_cs_document_id", columnList = "documentId"),
        @Index(name = "idx_cs_role", columnList = "role"),
        @Index(name = "idx_cs_name", columnList = "name"),
        @Index(name = "idx_cs_consolidated_at", columnList = "consolidatedAt")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_cs_doc_order", columnNames = {"documentId", "signatoryOrder"})
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidatedSignatory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * ID du document source (ex: "loi-2024-15").
     * Format : {@code {type}-{year}-{number}}
     */
    @Column(nullable = false, length = 50)
    private String documentId;
    
    /**
     * Ordre d'apparition dans le document (1, 2, 3...).
     * Permet maintenir l'ordre original des signatures.
     * Combiné avec {@code documentId} pour garantir unicité.
     */
    @Column(nullable = false)
    private Integer signatoryOrder;
    
    /**
     * Rôle du signataire (ex: "Président de la République", "Ministre des Finances").
     * Extrait depuis signatories[].role dans le JSON.
     */
    @Column(nullable = false, length = 200)
    private String role;
    
    /**
     * Nom complet du signataire (ex: "Patrice TALON").
     * Extrait depuis signatories[].name dans le JSON.
     */
    @Column(nullable = false, length = 200)
    private String name;
    
    /**
     * Date de début de mandat (optionnelle).
     * Extrait depuis signatories[].mandateStart dans le JSON.
     * Format LocalDate (YYYY-MM-DD).
     */
    @Column
    private LocalDate mandateStart;
    
    /**
     * Date de fin de mandat (optionnelle).
     * Extrait depuis signatories[].mandateEnd dans le JSON.
     * Format LocalDate (YYYY-MM-DD).
     */
    @Column
    private LocalDate mandateEnd;
    
    /**
     * Type de document (loi, decret).
     * Dénormalisé pour optimiser les requêtes.
     */
    @Column(length = 20)
    private String documentType;
    
    /**
     * Année du document (ex: 2024).
     * Dénormalisé pour optimiser les requêtes.
     */
    @Column
    private Integer documentYear;
    
    /**
     * Date de consolidation en base de données.
     * Permet audit et tracking temporal.
     */
    @Column(nullable = false)
    private LocalDateTime consolidatedAt;
}
