package bj.gouv.sgg.exception;

/**
 * Exception levée lors d'erreurs de traitement JSON
 * (parsing, sérialisation, validation)
 */
public class JsonProcessingException extends RuntimeException {

    public JsonProcessingException(String message) {
        super(message);
    }

    public JsonProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
