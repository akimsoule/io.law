package bj.gouv.sgg.exception;

import lombok.Getter;

/**
 * Exception levée lors de l'échec d'initialisation de Tesseract OCR.
 */
@Getter
public class TesseractInitializationException extends OcrProcessingException {
    
    private final String tessdataPath;
    private final int attempts;

    public TesseractInitializationException(String message) {
        // Utilise le constructeur OcrProcessingException qui accepte un message et une cause (null ici)
        super(message, (Throwable) null);
        // Initialise les champs finals avec des valeurs par défaut
        this.tessdataPath = null;
        this.attempts = 0;
    }
    
    public TesseractInitializationException(String tessdataPath, int attempts) {
        super("tessdata", String.format("Failed to initialize Tesseract after %d attempts (datapath=%s)", attempts, tessdataPath));
        this.tessdataPath = tessdataPath;
        this.attempts = attempts;
    }
    
    public TesseractInitializationException(String tessdataPath, String message, Throwable cause) {
        super("tessdata", String.format("Tesseract initialization failed (datapath=%s): %s", tessdataPath, message), cause);
        this.tessdataPath = tessdataPath;
        this.attempts = 0;
    }

}
