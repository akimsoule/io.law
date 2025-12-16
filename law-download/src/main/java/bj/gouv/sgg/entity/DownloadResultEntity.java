package bj.gouv.sgg.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité JPA pour les résultats de téléchargement PDF.
 * Trace les téléchargements effectués pour audit et idempotence.
 */
@Entity
@Table(
    name = "download_results",
    uniqueConstraints = @UniqueConstraint(columnNames = {"type", "document_year", "number"}),
    indexes = {
        @Index(name = "idx_download_success", columnList = "success"),
        @Index(name = "idx_download_type_year", columnList = "type, document_year"),
        @Index(name = "idx_download_downloaded_at", columnList = "downloaded_at"),
        @Index(name = "idx_download_sha256", columnList = "sha256_hash")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadResultEntity {
    
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
    private boolean success;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "downloaded_at", nullable = false)
    @Builder.Default
    private LocalDateTime downloadedAt = LocalDateTime.now();
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "sha256_hash", length = 64)
    private String sha256Hash;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    /**
     * Génère timestamps avant insertion.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (downloadedAt == null) {
            downloadedAt = LocalDateTime.now();
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
