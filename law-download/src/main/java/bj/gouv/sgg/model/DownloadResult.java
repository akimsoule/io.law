package bj.gouv.sgg.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité représentant les résultats de téléchargement de PDFs
 */
@Entity
@Table(name = "download_results", indexes = {
    @Index(name = "idx_download_document_id", columnList = "documentId"),
    @Index(name = "idx_downloaded_at", columnList = "downloadedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 50)
    private String documentId;
    
    @Column(nullable = false, length = 500)
    private String url;
    
    @Column(nullable = false, length = 500)
    private String pdfPath;
    
    @Column(length = 64)
    private String sha256;
    
    @Column(nullable = false)
    private Long fileSize;
    
    @Column(nullable = false)
    private LocalDateTime downloadedAt;
    
    @Column(length = 1000)
    private String errorMessage;
}
