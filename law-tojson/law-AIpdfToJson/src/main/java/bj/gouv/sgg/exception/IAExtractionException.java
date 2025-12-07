package bj.gouv.sgg.exception;

/**
 * Exception levée lors d'erreurs d'extraction via IA
 * (Ollama, Groq, ou autre provider)
 */
public class IAExtractionException extends RuntimeException {

    public IAExtractionException(String message) {
        super(message);
    }

    public IAExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
