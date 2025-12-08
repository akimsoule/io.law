package bj.gouv.sgg.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité représentant un article extrait d'un document
 */
@Entity
@Table(name = "article_extractions", indexes = {
    @Index(name = "idx_article_document_id", columnList = "documentId"),
    @Index(name = "idx_article_index", columnList = "articleIndex"),
    @Index(name = "idx_article_extracted_at", columnList = "extractedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleExtraction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 50)
    private String documentId;
    
    @Column(nullable = false)
    private Integer articleIndex;
    
    @Column(nullable = false, length = 200)
    private String title;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    
    @Column(nullable = false)
    private Double confidence;
    
    // Metadata fields
    @Column(length = 20)
    private String documentType;
    
    @Column
    private Integer documentYear;
    
    @Column
    private Integer documentNumber;
    
    @Column(length = 500)
    private String sourceUrl;
    
    @Column(length = 500)
    private String lawTitle;
    
    @Column(length = 50)
    private String promulgationDate;
    
    @Column(length = 100)
    private String promulgationCity;
    
    @Column(columnDefinition = "JSON")
    private String signatories;
    
    @Column(nullable = false)
    private LocalDateTime extractedAt;

}
