package bj.gouv.sgg.util;

import bj.gouv.sgg.exception.FileOperationException;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * Utilitaire pour standardiser la gestion d'erreurs à travers l'application
 */
@Slf4j
public class ErrorHandlingUtils {
    
    private static final String LOG_SUCCESS = "[{}] {} completed successfully";
    private static final String LOG_FAILED = "[{}] {} failed: {}";
    private static final String ERROR_MESSAGE_FORMAT = "[%s] %s failed";
    
    /**
     * Exécute une opération avec gestion d'erreurs standardisée
     * 
     * @param operation Opération à exécuter
     * @param operationName Nom de l'opération pour le logging
     * @param context Contexte (ex: document ID)
     * @param <T> Type de retour
     * @return Résultat de l'opération
     * @throws RuntimeException si l'opération échoue
     */
    public static <T> T executeWithLogging(
            Supplier<T> operation,
            String operationName,
            String context
    ) {
        try {
            T result = operation.get();
            log.debug(LOG_SUCCESS, context, operationName);
            return result;
        } catch (Exception e) {
            log.error(LOG_FAILED, context, operationName, e.getMessage(), e);
            throw new FileOperationException(
                String.format(ERROR_MESSAGE_FORMAT, context, operationName), e
            );
        }
    }
    
    /**
     * Exécute une opération avec fallback silencieux en cas d'erreur
     * 
     * @param operation Opération à exécuter
     * @param fallbackValue Valeur à retourner en cas d'erreur
     * @param operationName Nom de l'opération pour le logging
     * @param context Contexte (ex: document ID)
     * @param <T> Type de retour
     * @return Résultat de l'opération ou fallbackValue en cas d'erreur
     */
    public static <T> T executeWithFallback(
            Supplier<T> operation,
            T fallbackValue,
            String operationName,
            String context
    ) {
        try {
            return operation.get();
        } catch (Exception e) {
            log.warn("[{}] {} failed, using fallback: {}", 
                    context, operationName, e.getMessage());
            return fallbackValue;
        }
    }
    
    /**
     * Exécute une opération avec gestion d'erreurs qui retourne null en cas d'échec
     * 
     * @param operation Opération à exécuter
     * @param operationName Nom de l'opération pour le logging
     * @param context Contexte (ex: document ID)
     * @param <T> Type de retour
     * @return Résultat de l'opération ou null en cas d'erreur
     */
    public static <T> T executeOrNull(
            Supplier<T> operation,
            String operationName,
            String context
    ) {
        return executeWithFallback(operation, null, operationName, context);
    }
    
    /**
     * Exécute une opération qui ne retourne rien (void) avec gestion d'erreurs
     * 
     * @param operation Opération à exécuter
     * @param operationName Nom de l'opération pour le logging
     * @param context Contexte (ex: document ID)
     */
    public static void executeVoid(
            Runnable operation,
            String operationName,
            String context
    ) {
        try {
            operation.run();
            log.debug(LOG_SUCCESS, context, operationName);
        } catch (Exception e) {
            log.error(LOG_FAILED, context, operationName, e.getMessage(), e);
            throw new FileOperationException(
                String.format(ERROR_MESSAGE_FORMAT, context, operationName), e
            );
        }
    }
    
    /**
     * Exécute une opération void avec gestion silencieuse des erreurs
     * 
     * @param operation Opération à exécuter
     * @param operationName Nom de l'opération pour le logging
     * @param context Contexte (ex: document ID)
     * @return true si succès, false si erreur
     */
    public static boolean executeVoidSafely(
            Runnable operation,
            String operationName,
            String context
    ) {
        try {
            operation.run();
            log.debug("[{}] {} completed successfully", context, operationName);
            return true;
        } catch (Exception e) {
            log.warn("[{}] {} failed: {}", context, operationName, e.getMessage());
            return false;
        }
    }
    
    // Prevent instantiation
    private ErrorHandlingUtils() {}
}
