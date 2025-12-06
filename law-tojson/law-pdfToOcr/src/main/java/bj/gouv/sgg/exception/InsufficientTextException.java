package bj.gouv.sgg.exception;

/**
 * Exception levée quand le texte extrait par OCR est insuffisant (vide ou trop court).
 */
public class InsufficientTextException extends OcrProcessingException {
    
    private final int extractedLength;
    private final int minimumRequired;
    
    public InsufficientTextException(String documentId, int extractedLength, int minimumRequired) {
        super(documentId, 
              String.format("Insufficient text extracted from document %s: %d chars (minimum required: %d)", 
                            documentId, extractedLength, minimumRequired));
        this.extractedLength = extractedLength;
        this.minimumRequired = minimumRequired;
    }
    
    public int getExtractedLength() {
        return extractedLength;
    }
    
    public int getMinimumRequired() {
        return minimumRequired;
    }
}
