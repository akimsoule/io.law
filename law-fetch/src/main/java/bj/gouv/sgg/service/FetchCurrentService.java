package bj.gouv.sgg.service;

/**
 * Service de fetch pour l'année courante.
 * Vérifie l'existence des documents de l'année en cours.
 */
public interface FetchCurrentService {
    
    /**
     * Fetch tous les documents de l'année courante pour un type.
     * Vérifie les numéros de 1 à 2000.
     * 
     * @param type Type de document (loi/decret)
     */
    void run(String type);
}
