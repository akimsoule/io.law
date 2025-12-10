package bj.gouv.sgg.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Gestionnaire global des exceptions pour l'API REST.
 * Capture les exceptions et retourne des réponses JSON standardisées.
 * 
 * <p><b>Note</b> : Les exceptions spécifiques aux modules (PdfDownloadException, 
 * ArticleExtractionException, BatchProcessingException) sont gérées au niveau 
 * batch et héritent de LawProcessingException, donc capturées par le handler générique.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Gère les erreurs de document introuvable (404).
     */
    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDocumentNotFound(
            DocumentNotFoundException ex, 
            WebRequest request) {
        log.warn("Document not found: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.fromException(
                ex, 
                getPath(request), 
                HttpStatus.NOT_FOUND.value());
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    /**
     * Gère les erreurs de format de documentId invalide (400).
     */
    @ExceptionHandler(InvalidDocumentIdException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDocumentId(
            InvalidDocumentIdException ex, 
            WebRequest request) {
        log.warn("Invalid document ID: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.fromException(
                ex, 
                getPath(request), 
                HttpStatus.BAD_REQUEST.value());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * Gère les erreurs de stockage de fichiers (500).
     */
    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ErrorResponse> handleFileStorage(
            FileStorageException ex, 
            WebRequest request) {
        log.error("File storage error: {}", ex.getMessage(), ex);
        
        ErrorResponse error = ErrorResponse.fromException(
                ex, 
                getPath(request), 
                HttpStatus.INTERNAL_SERVER_ERROR.value());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    /**
     * Gère toutes les exceptions métier héritant de LawProcessingException (500).
     */
    @ExceptionHandler(LawProcessingException.class)
    public ResponseEntity<ErrorResponse> handleLawProcessing(
            LawProcessingException ex, 
            WebRequest request) {
        log.error("Law processing error: {}", ex.getMessage(), ex);
        
        ErrorResponse error = ErrorResponse.fromException(
                ex, 
                getPath(request), 
                HttpStatus.INTERNAL_SERVER_ERROR.value());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    /**
     * Gère toutes les exceptions non gérées (500).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, 
            WebRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        
        ErrorResponse error = ErrorResponse.create(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "InternalServerError",
                "An unexpected error occurred: " + ex.getMessage(),
                getPath(request));
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    /**
     * Extrait le chemin de la requête.
     */
    private String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
