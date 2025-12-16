package bj.gouv.sgg.exception;

/**
 * Exception levée lors du chargement d'un fichier prompt.
 * Cette exception est non-bloquante car catchée par PromptLoader.
 */
public class PromptLoadException extends IAException {
    
    public PromptLoadException(String message) {
        super(message);
    }

    public PromptLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
