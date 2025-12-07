package bj.gouv.sgg.exception;

/**
 * Exception levée lors d'erreurs de chargement de configuration
 * (patterns, signataires, dictionnaire)
 */
public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
