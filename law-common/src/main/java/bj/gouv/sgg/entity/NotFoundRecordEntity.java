package bj.gouv.sgg.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité JPA pour les documents introuvables (404).
 * Permet d'éviter de re-vérifier les mêmes documents.
 */
@Entity
@Table(
    name = "not_found_records",
    uniqueConstraints = @UniqueConstraint(columnNames = {"type", "document_year", "number"}),
    indexes = {
        @Index(name = "idx_notfound_type_year", columnList = "type, document_year"),
        @Index(name = "idx_notfound_checked_at", columnList = "checked_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotFoundRecordEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "document_id", nullable = false, unique = true, length = 50)
    private String documentId;
    
    @Column(nullable = false, length = 10)
    private String type;
    
    @Column(name = "document_year", nullable = false)
    private int year;
    
    @Column(nullable = false)
    private int number;
    
    @Column(columnDefinition = "TEXT")
    private String url;
    
    @Column(name = "checked_at", nullable = false)
    @Builder.Default
    private LocalDateTime checkedAt = LocalDateTime.now();
    
    @Column(name = "http_status_code")
    private int httpStatusCode;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    /**
     * Génère documentId et timestamp avant insertion.
     */
    @PrePersist
    protected void onCreate() {
        if (documentId == null && type != null && !type.isEmpty()) {
            documentId = String.format("%s-%d-%d", type, year, number);
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (checkedAt == null) {
            checkedAt = LocalDateTime.now();
        }
    }
}
