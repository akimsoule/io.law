package bj.gouv.sgg.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité JPA pour les résultats de fetch HTTP.
 * Trace si chaque document existe sur le serveur SGG.
 */
@Entity
@Table(
    name = "fetch_results",
    uniqueConstraints = @UniqueConstraint(columnNames = {"type", "document_year", "number"}),
    indexes = {
        @Index(name = "idx_fetch_found", columnList = "found"),
        @Index(name = "idx_fetch_type_year", columnList = "type, document_year"),
        @Index(name = "idx_fetch_fetched_at", columnList = "fetched_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchResultEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "document_id", nullable = false, length = 50)
    private String documentId;
    
    @Column(nullable = false, length = 10)
    private String type;
    
    @Column(name = "document_year", nullable = false)
    private int year;
    
    @Column(nullable = false)
    private int number;
    
    @Column(nullable = false)
    private boolean found;
    
    @Column(name = "http_status")
    private int httpStatus;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "fetched_at", nullable = false)
    @Builder.Default
    private LocalDateTime fetchedAt = LocalDateTime.now();
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    /**
     * Génère le documentId avant insertion si null.
     */
    @PrePersist
    protected void onCreate() {
        if (documentId == null && type != null && !type.isEmpty()) {
            documentId = String.format("%s-%d-%d", type, year, number);
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (fetchedAt == null) {
            fetchedAt = LocalDateTime.now();
        }
    }
    
    /**
     * Met à jour updatedAt avant modification.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
