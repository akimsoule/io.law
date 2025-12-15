package bj.gouv.sgg.service;

import bj.gouv.sgg.model.FetchCursor;
import bj.gouv.sgg.repository.FetchCursorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service dédié à la mise à jour du cursor en mode thread-safe.
 * Utilise REQUIRES_NEW pour isoler chaque transaction et éviter les deadlocks MySQL.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CursorUpdateService {

    private final FetchCursorRepository fetchCursorRepository;
    
    // Lock statique pour synchroniser l'accès entre tous les threads
    private static final Lock CURSOR_LOCK = new ReentrantLock();

    /**
     * Sauvegarde ou met à jour le cursor en base de données de manière thread-safe.
     * 
     * @param cursorType Le type de cursor (CURRENT ou PREVIOUS)
     * @param documentType Le type de document (loi ou decret)
     * @param currentYear L'année courante du cursor
     * @param currentNumber Le numéro courant du cursor
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateCursor(String cursorType, String documentType, Integer currentYear, Integer currentNumber) {
        CURSOR_LOCK.lock();
        try {
            Optional<FetchCursor> existingOpt = fetchCursorRepository
                .findByCursorTypeAndDocumentType(cursorType, documentType);
            
            FetchCursor cursorToSave;
            
            if (existingOpt.isPresent()) {
                // UPDATE : Mettre à jour cursor existant
                cursorToSave = existingOpt.get();
                cursorToSave.setCurrentYear(currentYear);
                cursorToSave.setCurrentNumber(currentNumber);
                log.debug("✅ Updated cursor for {} ({}) to year={}, number={}", 
                         documentType, cursorType, currentYear, currentNumber);
            } else {
                // INSERT : Créer nouveau cursor
                cursorToSave = FetchCursor.builder()
                    .cursorType(cursorType)
                    .documentType(documentType)
                    .currentYear(currentYear)
                    .currentNumber(currentNumber)
                    .build();
                log.info("➕ Created cursor for {} ({}) at year={}, number={}", 
                         documentType, cursorType, currentYear, currentNumber);
            }
            
            fetchCursorRepository.save(cursorToSave);
        } finally {
            CURSOR_LOCK.unlock();
        }
    }
}
