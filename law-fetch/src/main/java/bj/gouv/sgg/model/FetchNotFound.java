package bj.gouv.sgg.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité représentant un document NOT_FOUND (404)
 * Système simple : une ligne par document introuvable
 * Idempotence garantie par contrainte UNIQUE
 */
@Entity
@Table(name = "fetch_not_found", 
    uniqueConstraints = @UniqueConstraint(
        name = "uk_not_found_document",
        columnNames = {"document_type", "document_year", "document_number"}
    ),
    indexes = {
        @Index(name = "idx_not_found_type_year", columnList = "document_type,document_year"),
        @Index(name = "idx_not_found_detected_at", columnList = "detected_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchNotFound {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "document_type", nullable = false, length = 20)
    private String documentType; // "loi" ou "decret"
    
    @Column(name = "document_year", nullable = false)
    private Integer year;
    
    @Column(name = "document_number", nullable = false)
    private Integer number;
    
    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;
    
    /**
     * Retourne l'identifiant du document
     * Format: loi-2024-15
     */
    public String getDocumentId() {
        return String.format("%s-%d-%d", documentType, year, number);
    }
    
    @PrePersist
    protected void onCreate() {
        if (detectedAt == null) {
            detectedAt = LocalDateTime.now();
        }
    }
}
