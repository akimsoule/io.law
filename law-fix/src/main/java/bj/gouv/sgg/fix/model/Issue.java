package bj.gouv.sgg.fix.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Représente un problème détecté dans le système.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Issue {
    
    private String documentId;
    private IssueType type;
    private IssueSeverity severity;
    private String description;
    private String currentStatus;
    private String suggestedAction;
    private LocalDateTime detectedAt;
    private boolean autoFixable;
    
    public enum IssueType {
        // Problèmes de statut
        STUCK_IN_PENDING("Document bloqué en PENDING", true),
        STUCK_IN_FETCHED("Document bloqué en FETCHED", true),
        STUCK_IN_DOWNLOADED("Document bloqué en DOWNLOADED", true),
        STUCK_IN_EXTRACTED("Document bloqué en EXTRACTED", true),
        
        // Problèmes de fichiers
        MISSING_PDF("PDF manquant alors que status=DOWNLOADED", true),
        MISSING_OCR("OCR manquant alors que status=EXTRACTED", true),
        MISSING_JSON("JSON manquant alors que status=EXTRACTED", true),
        CORRUPTED_PDF("PDF corrompu détecté", false),
        
        // Problèmes de qualité
        LOW_CONFIDENCE("Confiance extraction trop faible (<0.3)", true),
        SEQUENCE_ISSUES("Problèmes séquence articles détectés", true),
        HIGH_UNRECOGNIZED_WORDS("Trop de mots non reconnus (>50%)", true),
        
        // Problèmes de consolidation
        CONSOLIDATION_FAILED("Échec consolidation en BD", true),
        MISSING_ARTICLES("Document sans articles consolidés", true),
        INCONSISTENT_DATA("Données incohérentes entre JSON et BD", true),
        
        // Problèmes réseau/SGG
        URL_NOT_FOUND_404("URL SGG retourne 404", false),
        DOWNLOAD_TIMEOUT("Timeout lors du téléchargement", true);
        
        private final String description;
        private final boolean autoFixable;
        
        IssueType(String description, boolean autoFixable) {
            this.description = description;
            this.autoFixable = autoFixable;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean isAutoFixable() {
            return autoFixable;
        }
    }
    
    public enum IssueSeverity {
        CRITICAL,  // Bloque le pipeline
        HIGH,      // Impact qualité majeur
        MEDIUM,    // Impact qualité mineur
        LOW        // Optimisation possible
    }
}
