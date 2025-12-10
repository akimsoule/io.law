package bj.gouv.sgg.fix.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Résultat d'une tentative de correction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixResult {
    
    private String documentId;
    private Issue.IssueType issueType;
    private FixStatus status;
    private String action;
    private String details;
    private LocalDateTime fixedAt;
    private int retryCount;
    
    public enum FixStatus {
        SUCCESS("Corrigé avec succès"),
        PARTIAL("Correction partielle"),
        FAILED("Échec correction"),
        SKIPPED("Ignoré (non auto-fixable)"),
        RETRY_SCHEDULED("Nouvelle tentative programmée");
        
        private final String description;
        
        FixStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}
