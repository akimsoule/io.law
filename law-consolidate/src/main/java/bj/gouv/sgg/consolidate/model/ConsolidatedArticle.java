package bj.gouv.sgg.consolidate.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité représentant un article consolidé en base de données.
 * 
 * <p>Cette table stocke les articles extraits depuis les fichiers JSON générés
 * par le module law-ocr-json. Chaque article est lié à un document via
 * {@code documentId}.
 * 
 * <p><b>Schéma</b> :
 * <ul>
 *   <li>PK : {@code id} (auto-increment)</li>
 *   <li>UK : {@code documentId + articleIndex} (éviter doublons)</li>
 *   <li>Index : {@code documentId}, {@code documentType}, {@code documentYear}</li>
 * </ul>
 * 
 * <p><b>Idempotence</b> : La contrainte {@code UNIQUE(documentId, articleIndex)}
 * garantit qu'un article n'est inséré qu'une seule fois. En cas de re-run,
 * l'UPDATE remplace l'ancienne version.
 * 
 * @see bj.gouv.sgg.consolidate.service.ConsolidationService
 */
@Entity
@Table(
    name = "consolidated_articles",
    indexes = {
        @Index(name = "idx_ca_document_id", columnList = "documentId"),
        @Index(name = "idx_ca_document_type", columnList = "documentType"),
        @Index(name = "idx_ca_document_year", columnList = "documentYear"),
        @Index(name = "idx_ca_consolidated_at", columnList = "consolidatedAt")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_ca_doc_article", columnNames = {"documentId", "articleIndex"})
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidatedArticle {
    
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
     * Numéro d'ordre de l'article (1, 2, 3...).
     * Combiné avec {@code documentId} pour garantir unicité.
     */
    @Column(nullable = false)
    private Integer articleIndex;
    
    /**
     * Contenu textuel complet de l'article.
     * Peut contenir plusieurs paragraphes, sauts de ligne.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    
    /**
     * Type de document (loi, decret).
     * Dénormalisé pour optimiser les requêtes par type.
     */
    @Column(length = 20)
    private String documentType;
    
    /**
     * Année du document (ex: 2024).
     * Dénormalisé pour optimiser les requêtes par année.
     */
    @Column
    private Integer documentYear;
    
    /**
     * Numéro du document dans son année (ex: 15 pour loi-2024-15).
     * Dénormalisé pour référence rapide.
     */
    @Column
    private Integer documentNumber;
    
    /**
     * Confiance de l'extraction (0.0 à 1.0).
     * Héritée du fichier JSON (_metadata.confidence).
     * Permet filtrage articles haute qualité.
     */
    @Column
    private Double extractionConfidence;
    
    /**
     * Méthode d'extraction utilisée (ex: "OCR:PROGRAMMATIC", "IA:OLLAMA").
     * Héritée du fichier JSON (_metadata.source).
     */
    @Column(length = 50)
    private String extractionMethod;
    
    /**
     * Date de consolidation en base de données.
     * Permet audit et tracking temporal.
     */
    @Column(nullable = false)
    private LocalDateTime consolidatedAt;
}
