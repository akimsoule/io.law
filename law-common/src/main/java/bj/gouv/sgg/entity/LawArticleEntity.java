package bj.gouv.sgg.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité JPA pour les articles consolidés d'une loi ou d'un décret.
 * Stocke le contenu structuré après consolidation.
 */
@Entity
@Table(name = "law_articles",
        indexes = {
                @Index(name = "idx_document_id", columnList = "document_id"),
                @Index(name = "idx_article_number", columnList = "document_id,article_number")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LawArticleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false, length = 50)
    private String documentId; // Référence vers law_documents.document_id

    @Column(name = "article_number", length = 20)
    private String articleNumber; // "1", "2", "bis", etc.

    @Lob
    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content; // Contenu texte de l'article

    @Lob
    @Column(name = "raw_json", columnDefinition = "LONGTEXT")
    private String rawJson; // Structure JSON complète de l'article

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
