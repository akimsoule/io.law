package bj.gouv.sgg.service;

import org.springframework.stereotype.Service;

/**
 * Service HTTP pour vérifier l'existence des documents.
 * Permet de mocker les appels HTTP dans les tests.
 */
@Service
public class HttpCheckService {
    
    /**
     * Vérifie l'existence d'un document via HEAD request.
     * 
     * @param url URL du document à vérifier
     * @param userAgent User agent à utiliser pour la requête
     * @return Code HTTP de la réponse (200, 404, 429, 500, etc.)
     */
    public int checkDocument(String url, String userAgent) {
        // Implémentation réelle dans le processor
        // Cette méthode sera appelée par le processor
        throw new UnsupportedOperationException("Should be implemented in processor or mocked in tests");
    }
}
