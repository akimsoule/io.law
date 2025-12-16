package bj.gouv.sgg.service;

import bj.gouv.sgg.config.DatabaseConfig;
import bj.gouv.sgg.entity.FetchCursorEntity;
import bj.gouv.sgg.repository.impl.JpaFetchCursorRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Service pour gérer les curseurs de fetch.
 * Utilise JPA/MySQL au lieu de JsonStorage.
 */
@Slf4j
public class FetchCursorService {
    
    private final JpaFetchCursorRepository repository;
    
    public FetchCursorService() {
        this.repository = new JpaFetchCursorRepository(
            DatabaseConfig.getInstance().createEntityManager()
        );
    }
    
    /**
     * Met à jour le cursor pour un type et mode.
     */
    public void updateCursor(String type, String mode, int year, int number) {
        Optional<FetchCursorEntity> existing = repository.findByCursorTypeAndDocumentType(mode, type);
        
        if (existing.isPresent()) {
            FetchCursorEntity cursor = existing.get();
            cursor.setCurrentYear(year);
            cursor.setCurrentNumber(number);
            repository.save(cursor);
            log.debug("✅ Updated cursor for {} ({}) to year={}, number={}", 
                     type, mode, year, number);
        } else {
            FetchCursorEntity cursor = FetchCursorEntity.builder()
                .cursorType(mode)
                .documentType(type)
                .currentYear(year)
                .currentNumber(number)
                .build();
            repository.save(cursor);
            log.debug("✅ Created cursor for {} ({}) at year={}, number={}", 
                     type, mode, year, number);
        }
    }
    
    /**
     * Récupère le cursor pour un type et mode.
     */
    public Optional<FetchCursorEntity> getCursor(String type, String mode) {
        return repository.findByCursorTypeAndDocumentType(mode, type);
    }
}
