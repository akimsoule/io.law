package bj.gouv.sgg.consolidate.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité représentant les métadonnées consolidées d'un document légal.
 * 
 * <p>Cette table stocke les métadonnées extraites depuis les fichiers JSON :
 * titre, date de promulgation, ville, référence JO, etc.
 * 
 * <p><b>Schéma</b> :
 * <ul>
 *   <li>PK : {@code id} (auto-increment)</li>
 *   <li>UK : {@code documentId} (1 metadata par document)</li>
 *   <li>Index : {@code documentType}, {@code documentYear}, {@code promulgationDate}</li>
 * </ul>
 * 
 * <p><b>Idempotence</b> : La contrainte {@code UNIQUE(documentId)} garantit
 * qu'un document n'a qu'une seule entrée de metadata. En cas de re-run,
 * l'UPDATE remplace l'ancienne version.
 * 
 * @see bj.gouv.sgg.consolidate.model.ConsolidatedArticle
 * @see bj.gouv.sgg.consolidate.model.ConsolidatedSignatory
 */
@Entity
@Table(
    name = "consolidated_metadata",
    indexes = {
        @Index(name = "idx_cm_document_type", columnList = "documentType"),
        @Index(name = "idx_cm_document_year", columnList = "documentYear"),
        @Index(name = "idx_cm_promulgation_date", columnList = "promulgationDate"),
        @Index(name = "idx_cm_consolidated_at", columnList = "consolidatedAt")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_cm_document_id", columnNames = {"documentId"})
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidatedMetadata {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * ID du document source (ex: "loi-2024-15").
     * Format : {@code {type}-{year}-{number}}
     */
    @Column(nullable = false, unique = true, length = 50)
    private String documentId;
    
    /**
     * Type de document (loi, decret).
     */
    @Column(nullable = false, length = 20)
    private String documentType;
    
    /**
     * Année du document (ex: 2024).
     */
    @Column(nullable = false)
    private Integer documentYear;
    
    /**
     * Numéro du document dans son année (ex: 15).
     */
    @Column(nullable = false)
    private Integer documentNumber;
    
    /**
     * Titre complet du document (ex: "Loi n° 2024-15 portant répression de l'usure").
     * Extrait depuis le JSON ou calculé depuis type/year/number.
     */
    @Column(length = 1000)
    private String title;
    
    /**
     * Date de promulgation du document (format ISO: YYYY-MM-DD).
     * Extrait depuis le JSON (promulgationDate).
     */
    @Column(length = 20)
    private String promulgationDate;
    
    /**
     * Ville de promulgation (ex: "Cotonou").
     * Extrait depuis le JSON (promulgationCity).
     */
    @Column(length = 100)
    private String promulgationCity;
    
    /**
     * Référence du Journal Officiel (ex: "N° 12 du 15 mars 2024").
     * Optionnel, extrait depuis metadata.journalOfficielRef si disponible.
     */
    @Column(length = 200)
    private String journalOfficielRef;
    
    /**
     * Date de l'assemblée nationale (format ISO: YYYY-MM-DD).
     * Optionnel, extrait depuis metadata.assemblyDate si disponible.
     */
    @Column(length = 20)
    private String assemblyDate;
    
    /**
     * Nombre total d'articles dans le document.
     * Calculé depuis la taille du tableau articles[].
     */
    @Column
    private Integer totalArticles;
    
    /**
     * URL source du document sur le site SGG (ex: https://sgg.gouv.bj/doc/loi-2024-15.pdf).
     * Permet traçabilité vers la source originale.
     */
    @Column(length = 500)
    private String sourceUrl;
    
    /**
     * Confiance de l'extraction (0.0 à 1.0).
     * Héritée du fichier JSON (_metadata.confidence).
     */
    @Column
    private Double extractionConfidence;
    
    /**
     * Méthode d'extraction utilisée (ex: "OCR:PROGRAMMATIC").
     * Héritée du fichier JSON (_metadata.source).
     */
    @Column(length = 50)
    private String extractionMethod;
    
    /**
     * Timestamp d'extraction depuis le JSON (_metadata.timestamp).
     * Format ISO-8601 (ex: "2025-12-07T16:58:19.582425Z").
     */
    @Column(length = 50)
    private String extractionTimestamp;
    
    /**
     * Date de consolidation en base de données.
     * Permet audit et tracking temporal.
     */
    @Column(nullable = false)
    private LocalDateTime consolidatedAt;
}
