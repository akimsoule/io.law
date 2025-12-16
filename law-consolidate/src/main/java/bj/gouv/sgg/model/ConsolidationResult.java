package bj.gouv.sgg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Résultat d'une consolidation de document.
 * Trace les consolidations effectuées pour audit et idempotence.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidationResult {
    
    private String documentId;
    private String type;
    private int year;
    private int number;
    private boolean success;
    private String errorMessage;
    private LocalDateTime consolidatedAt;
    private long jsonFileSize;
    
    /**
     * Crée un résultat de succès.
     */
    public static ConsolidationResult success(String documentId, String type, int year, int number, long jsonFileSize) {
        return ConsolidationResult.builder()
            .documentId(documentId)
            .type(type)
            .year(year)
            .number(number)
            .success(true)
            .consolidatedAt(LocalDateTime.now())
            .jsonFileSize(jsonFileSize)
            .build();
    }
    
    /**
     * Crée un résultat d'échec.
     */
    public static ConsolidationResult failure(String documentId, String type, int year, int number, String errorMessage) {
        return ConsolidationResult.builder()
            .documentId(documentId)
            .type(type)
            .year(year)
            .number(number)
            .success(false)
            .errorMessage(errorMessage)
            .consolidatedAt(LocalDateTime.now())
            .build();
    }
}
