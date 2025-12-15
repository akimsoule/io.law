package bj.gouv.sgg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * POJO représentant les résultats de fetch
 * Persisté via JsonStorage
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchResult {
    
    private String documentId;
    private String documentType;
    private Integer year;
    private Integer number;
    private String url;
    private String status;
    private Boolean exists;
    private LocalDateTime fetchedAt;
    private String errorMessage;
}
