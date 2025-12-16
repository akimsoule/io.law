package bj.gouv.sgg.exception;

import bj.gouv.sgg.exception.LawProcessingException;

/**
 * Exception levée lors d'erreurs de chargement de configuration
 * (patterns, signataires, dictionnaire).
 * Cette exception est non-bloquante car catchée par ArticleExtractorConfig.
 */
public class ConfigurationException extends LawProcessingException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
