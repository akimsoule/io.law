package bj.gouv.sgg.exception;

/**
 * Exception levée lors de la sauvegarde du JSON de sortie
 */
public class JsonOutputException extends RuntimeException {
    public JsonOutputException(String message) {
        super(message);
    }

    public JsonOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}
