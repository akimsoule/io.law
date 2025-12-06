package bj.gouv.sgg.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Réponse d'erreur standardisée pour l'API REST.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private String errorCode;
    private String documentId;
    
    public static ErrorResponse fromException(LawProcessingException ex, String path, int status) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status)
                .error(ex.getClass().getSimpleName())
                .message(ex.getMessage())
                .path(path)
                .errorCode(ex.getErrorCode())
                .documentId(ex.getDocumentId())
                .build();
    }
    
    public static ErrorResponse create(int status, String error, String message, String path) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .build();
    }
}
