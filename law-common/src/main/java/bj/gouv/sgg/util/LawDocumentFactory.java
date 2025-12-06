package bj.gouv.sgg.util;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.LawDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Factory pour créer des instances de LawDocument.
 * Centralise la logique de création et construction des URLs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LawDocumentFactory {
    
    private final LawProperties properties;
    
    /**
     * Crée un nouveau document de loi/décret avec URL construite
     *
     * @param type Type de document ("loi" ou "decret")
     * @param year Année du document
     * @param number Numéro du document
     * @return Instance LawDocument avec URL et statut PENDING
     */
    public LawDocument create(String type, int year, int number) {
        String url = buildUrl(type, year, number);
        
        return LawDocument.builder()
            .type(type)
            .year(year)
            .number(number)
            .url(url)
            .status(LawDocument.ProcessingStatus.PENDING)
            .build();
    }
    
    /**
     * Construit l'URL du document sur sgg.gouv.bj
     *
     * @param type Type de document ("loi" ou "decret")
     * @param year Année du document
     * @param number Numéro du document
     * @return URL complète (sans extension)
     */
    private String buildUrl(String type, int year, int number) {
        // Format: https://sgg.gouv.bj/doc/loi-2024-15
        return String.format("%s/%s-%d-%d", 
            properties.getBaseUrl(), 
            type, 
            year, 
            number
        );
    }
}
