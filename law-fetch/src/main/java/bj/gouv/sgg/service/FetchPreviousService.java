package bj.gouv.sgg.service;

/**
 * Service de fetch pour les années précédentes.
 * Vérifie l'existence des documents historiques.
 */
public interface FetchPreviousService {
    
    /**
     * Fetch les documents des années précédentes pour un type.
     * Balaye les années de (année courante - 1) jusqu'à 1960.
     * 
     * @param type Type de document (loi/decret)
     * @param maxItems Nombre maximum de documents à vérifier
     */
    void run(String type, int maxItems);
}
