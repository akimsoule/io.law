package bj.gouv.sgg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * POJO cursor pour le job fetchPrevious
 * Sauvegarde la position courante du scan des années précédentes (1960 à année-1)
 * Persisté via JsonStorage
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchCursor {

    public static final String CURSOR_TYPE_FETCH_PREVIOUS = "fetch-previous";
    public static final String CURSOR_TYPE_FETCH_CURRENT = "fetch-current";
    
    private String cursorType; // "fetch-previous" ou "fetch-current"
    private String documentType; // "loi" ou "decret"
    private Integer currentYear;
    private Integer currentNumber;
    private LocalDateTime updatedAt;
}
