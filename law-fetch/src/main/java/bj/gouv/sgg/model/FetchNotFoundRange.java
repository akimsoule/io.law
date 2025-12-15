package bj.gouv.sgg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * POJO représentant les plages de documents NOT_FOUND
 * Format: loi;2025;19-300 signifie que loi-2025-19 à loi-2025-300 n'existent pas
 * Persisté via JsonStorage
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchNotFoundRange {
    
    private String documentType; // "loi" ou "decret"
    private Integer year;
    private Integer numberMin;
    private Integer numberMax;
    private Integer documentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    /**
     * Retourne la représentation textuelle de la plage
     * Format: loi;2025;19-300
     */
    public String toRangeString() {
        if (numberMin.equals(numberMax)) {
            return String.format("%s;%d;%d", documentType, year, numberMin);
        }
        return String.format("%s;%d;%d-%d", documentType, year, numberMin, numberMax);
    }
    
    /**
     * Vérifie si un numéro est dans cette plage
     */
    public boolean contains(int number) {
        return number >= numberMin && number <= numberMax;
    }
    
    /**
     * Vérifie si cette plage peut être fusionnée avec une autre (adjacente ou chevauchante)
     */
    public boolean canMergeWith(FetchNotFoundRange other) {
        if (!this.documentType.equals(other.documentType) || !this.year.equals(other.year)) {
            return false;
        }
        
        // Chevauchement ou adjacence
        return (this.numberMax >= other.numberMin - 1 && this.numberMin <= other.numberMax + 1);
    }
    
    /**
     * Fusionne cette plage avec une autre
     */
    public void mergeWith(FetchNotFoundRange other) {
        this.numberMin = Math.min(this.numberMin, other.numberMin);
        this.numberMax = Math.max(this.numberMax, other.numberMax);
        this.updatedAt = LocalDateTime.now();
    }
}
