package bj.gouv.sgg.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité représentant les résultats d'extraction OCR
 */
@Entity
@Table(name = "ocr_results", indexes = {
    @Index(name = "idx_ocr_document_id", columnList = "documentId"),
    @Index(name = "idx_ocr_extracted_at", columnList = "extractedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 50)
    private String documentId;
    
    @Column(nullable = false, length = 500)
    private String pdfPath;
    
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String ocrText;
    
    @Column(nullable = false)
    private Integer textLength;
    
    @Column
    private Double quality;
    
    @Column(nullable = false)
    private LocalDateTime extractedAt;
}
