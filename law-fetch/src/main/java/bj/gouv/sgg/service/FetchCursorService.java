package bj.gouv.sgg.service;

import bj.gouv.sgg.entity.FetchCursorEntity;
import bj.gouv.sgg.repository.FetchCursorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service pour gérer les curseurs de fetch.
 * Migré vers Spring Data JPA.
 * Utilise verrou pessimiste pour éviter duplications en multi-threading.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FetchCursorService {
    
    private final FetchCursorRepository repository;
    
    /**
     * Met à jour le cursor pour un type et mode.
     * Utilise REQUIRES_NEW pour isoler la transaction et activer le verrou pessimiste.
     * Synchronized pour double sécurité au niveau JVM.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized void updateCursor(String type, String mode, int year, int number) {
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
     * Transactional READ_ONLY pour activer le verrou pessimiste en lecture.
     * Synchronized pour cohérence avec updateCursor.
     */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public synchronized Optional<FetchCursorEntity> getCursor(String type, String mode) {
        return repository.findByCursorTypeAndDocumentType(mode, type);
    }
}
