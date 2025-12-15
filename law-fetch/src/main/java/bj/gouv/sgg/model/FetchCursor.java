package bj.gouv.sgg.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Cursor pour le job fetchPrevious
 * Sauvegarde la position courante du scan des années précédentes (1960 à année-1)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fetch_cursor",
    uniqueConstraints = @UniqueConstraint(columnNames = {"cursorType", "documentType"}),
    indexes = @Index(name = "idx_cursor_type_document_type", columnList = "cursorType, documentType")
)
public class FetchCursor {

    public static final String CURSOR_TYPE_FETCH_PREVIOUS = "fetch-previous";
    public static final String CURSOR_TYPE_FETCH_CURRENT = "fetch-current";
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 50)
    private String cursorType; // "fetch-previous" ou "fetch-current"
    
    @Column(nullable = false, length = 20)
    private String documentType; // "loi" ou "decret"
    
    @Column(nullable = false)
    private Integer currentYear;
    
    @Column(nullable = false)
    private Integer currentNumber;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
