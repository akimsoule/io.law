package bj.gouv.sgg.exception;

/**
 * Exception levée lors de l'échec d'initialisation de Tesseract OCR.
 */
public class TesseractInitializationException extends OcrProcessingException {
    
    private final String tessdataPath;
    private final int attempts;
    
    public TesseractInitializationException(String tessdataPath, int attempts) {
        super(String.format("Failed to initialize Tesseract after %d attempts (datapath=%s)", attempts, tessdataPath), null);
        this.tessdataPath = tessdataPath;
        this.attempts = attempts;
    }
    
    public TesseractInitializationException(String tessdataPath, String message, Throwable cause) {
        super(String.format("Tesseract initialization failed (datapath=%s): %s", tessdataPath, message), cause);
        this.tessdataPath = tessdataPath;
        this.attempts = 0;
    }
    
    public String getTessdataPath() {
        return tessdataPath;
    }
    
    public int getAttempts() {
        return attempts;
    }
}
