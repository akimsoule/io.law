package bj.gouv.sgg.exception;

/**
 * Exception levée lors du chargement d'un fichier prompt
 */
public class PromptLoadException extends RuntimeException {
    public PromptLoadException(String message) {
        super(message);
    }

    public PromptLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
