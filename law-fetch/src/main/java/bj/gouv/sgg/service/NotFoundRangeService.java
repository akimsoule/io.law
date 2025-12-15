package bj.gouv.sgg.service;

import bj.gouv.sgg.model.FetchNotFoundRange;
import bj.gouv.sgg.storage.JsonStorage;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Service pour gérer les plages de numéros non trouvés (optimisation future).
 * Permet de détecter les plages continues de 404 pour éviter de re-scanner.
 */
@Slf4j
public class NotFoundRangeService {
    
    private final JsonStorage<FetchNotFoundRange> storage;
    
    public NotFoundRangeService() {
        Path storagePath = Paths.get("data/db/not_found_ranges.json");
        this.storage = new JsonStorage<>(storagePath, new TypeToken<>() {});
    }
    
    /**
     * Enregistre une plage de numéros non trouvés.
     * 
     * @param type Type de document
     * @param year Année
     * @param startNumber Numéro de début de la plage
     * @param endNumber Numéro de fin de la plage
     */
    public void recordNotFoundRange(String type, int year, int startNumber, int endNumber) {
        if (endNumber - startNumber < 10) {
            // Ne pas enregistrer les petites plages
            return;
        }
        
        FetchNotFoundRange range = FetchNotFoundRange.builder()
                .type(type)
                .year(year)
                .startNumber(startNumber)
                .endNumber(endNumber)
                .detectedAt(java.time.LocalDateTime.now())
                .build();
        
        storage.append(range);
        log.debug("📝 Recorded not-found range: {}-{}-{} to {}", type, year, startNumber, endNumber);
    }
    
    /**
     * Vérifie si un numéro est dans une plage connue de non trouvés.
     * 
     * @param type Type de document
     * @param year Année
     * @param number Numéro à vérifier
     * @return true si le numéro est dans une plage connue de 404
     */
    public boolean isInNotFoundRange(String type, int year, int number) {
        List<FetchNotFoundRange> ranges = storage.readAll();
        
        return ranges.stream()
                .anyMatch(range -> 
                    range.getType().equals(type) &&
                    range.getYear() == year &&
                    number >= range.getStartNumber() &&
                    number <= range.getEndNumber()
                );
    }
    
    /**
     * Récupère toutes les plages non trouvées pour un type et une année.
     */
    public List<FetchNotFoundRange> getRanges(String type, int year) {
        return storage.findAll(range -> 
            range.getType().equals(type) && range.getYear() == year
        );
    }
}
