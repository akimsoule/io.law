package bj.gouv.sgg.exception;

/**
 * Exception levée lors d'erreurs d'extraction via IA
 * (Ollama, Groq, ou autre provider).
 * Cette exception est non-bloquante car catchée par AIOrchestrator.
 */
public class IAExtractionException extends IAException {

    public IAExtractionException(String message) {
        super(message);
    }

    public IAExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
