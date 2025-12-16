package bj.gouv.sgg.entity;

import bj.gouv.sgg.model.ProcessingStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité JPA pour les documents de loi.
 * Stockage MySQL pour éviter les problèmes de fichiers JSON corrompus.
 */
@Entity
@Table(name = "law_documents", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"type", "document_year", "number"}),
       indexes = {
           @Index(name = "idx_status", columnList = "status"),
           @Index(name = "idx_type_year", columnList = "type,document_year")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LawDocumentEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "document_id", nullable = false, length = 50, unique = true)
    private String documentId; // Format: type-year-number (ex: loi-2024-15)
    
    @Column(nullable = false, length = 10)
    private String type; // loi ou decret
    
    @Column(name = "document_year", nullable = false)
    private int year;
    
    @Column(nullable = false)
    private int number;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProcessingStatus status;
    
    @Column(length = 500)
    private String url; // URL sur sgg.gouv.bj
    
    @Column(columnDefinition = "TEXT")
    private String title; // Titre du document
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @Column(name = "pdf_path", length = 255)
    private String pdfPath;
    
    @Column(name = "ocr_path", length = 255)
    private String ocrPath;
    
    @Column(name = "json_path", length = 255)
    private String jsonPath;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        
        // Générer documentId si manquant
        if (documentId == null && type != null && !type.isEmpty()) {
            documentId = String.format("%s-%d-%d", type, year, number);
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
