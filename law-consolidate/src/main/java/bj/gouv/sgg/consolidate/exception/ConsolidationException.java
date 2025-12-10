package bj.gouv.sgg.consolidate.exception;

/**
 * Exception levée lors d'erreurs de consolidation JSON → MySQL.
 * 
 * <p><b>Cas d'usage</b> :
 * <ul>
 *   <li>Fichier JSON introuvable ou illisible</li>
 *   <li>JSON mal formé (parsing échoué)</li>
 *   <li>Données invalides (contraintes non respectées)</li>
 *   <li>Erreur persistance base de données</li>
 * </ul>
 * 
 * @see bj.gouv.sgg.consolidate.service.ConsolidationService
 */
public class ConsolidationException extends RuntimeException {
    
    public ConsolidationException(String message) {
        super(message);
    }
    
    public ConsolidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
