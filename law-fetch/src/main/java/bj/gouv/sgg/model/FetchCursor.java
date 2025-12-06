package bj.gouv.sgg.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fetch_cursor",
    uniqueConstraints = @UniqueConstraint(columnNames = {"cursorType", "documentType"}),
    indexes = @Index(name = "idx_cursor_type", columnList = "cursorType")
)
public class FetchCursor {
    
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
}
