package bj.gouv.sgg.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entité JPA pour les cursors de fetch (position dans le scan).
 * Table: fetch_cursor
 */
@Entity
@Table(name = "fetch_cursor",
    indexes = {
        @Index(name = "idx_cursor_type", columnList = "cursor_type")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchCursorEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "cursor_type", nullable = false, length = 50)
    private String cursorType;
    
    @Column(name = "document_type", nullable = false, length = 20)
    private String documentType;
    
    @Column(name = "current_year", nullable = false)
    private int currentYear;
    
    @Column(name = "current_number", nullable = false)
    private int currentNumber;
    
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Crée un cursor pour fetchPrevious.
     */
    public static FetchCursorEntity createFetchPrevious(String documentType, int startYear, int startNumber) {
        return FetchCursorEntity.builder()
            .cursorType("fetch-previous")
            .documentType(documentType)
            .currentYear(startYear)
            .currentNumber(startNumber)
            .updatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Crée un cursor pour fetchCurrent.
     */
    public static FetchCursorEntity createFetchCurrent(String documentType, int currentYear, int lastNumber) {
        return FetchCursorEntity.builder()
            .cursorType("fetch-current")
            .documentType(documentType)
            .currentYear(currentYear)
            .currentNumber(lastNumber)
            .updatedAt(LocalDateTime.now())
            .build();
    }
}
