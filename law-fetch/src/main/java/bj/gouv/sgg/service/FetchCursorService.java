package bj.gouv.sgg.service;

import bj.gouv.sgg.entity.FetchCursorEntity;
import bj.gouv.sgg.repository.FetchCursorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service pour gérer les curseurs de fetch.
 * Spring Batch gère les transactions au niveau chunk.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FetchCursorService {
    
    private final FetchCursorRepository repository;
    
    /**
     * Met à jour le cursor pour un type et mode.
     * Spring Batch gère les transactions au niveau chunk.
     */
    public void updateCursor(String type, String mode, int year, int number) {
        try {
            Optional<FetchCursorEntity> existing = repository.findByCursorTypeAndDocumentType(mode, type);
            
            if (existing.isPresent()) {
                // Mise à jour
                FetchCursorEntity cursor = existing.get();
                cursor.setCurrentYear(year);
                cursor.setCurrentNumber(number);
                repository.save(cursor);
                log.debug("✅ Updated cursor for {} ({}) to year={}, number={}", 
                         type, mode, year, number);
            } else {
                // Création
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
        } catch (Exception e) {
            log.error("❌ Failed to update cursor for {} ({}): {}", type, mode, e.getMessage());
            // Ne pas relancer l'exception pour ne pas bloquer le chunk entier
            // Le cursor restera à sa position précédente
        }
    }
    
    /**
     * Récupère le cursor pour un type et mode.
     */
    public Optional<FetchCursorEntity> getCursor(String type, String mode) {
        return repository.findByCursorTypeAndDocumentType(mode, type);
    }
}
