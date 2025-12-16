package bj.gouv.sgg.service;

import bj.gouv.sgg.config.DatabaseConfig;
import bj.gouv.sgg.entity.NotFoundRecordEntity;
import bj.gouv.sgg.repository.impl.JpaNotFoundRecordRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service pour gérer les documents NOT_FOUND.
 * Utilise JPA/MySQL au lieu de JsonStorage.
 */
@Slf4j
public class NotFoundRecordService {
    
    private final JpaNotFoundRecordRepository repository;
    
    public NotFoundRecordService() {
        this.repository = new JpaNotFoundRecordRepository(
            DatabaseConfig.getInstance().createEntityManager()
        );
    }
    
    /**
     * Sauvegarde un record NOT_FOUND.
     */
    public void save(String documentId, String type, int year, int number) {
        Optional<NotFoundRecordEntity> existing = repository.findByDocumentId(documentId);
        
        if (existing.isPresent()) {
            // Update timestamp
            NotFoundRecordEntity record = existing.get();
            record.setCheckedAt(LocalDateTime.now());
            repository.save(record);
            log.debug("🔄 Updated not found: {}", documentId);
        } else {
            // Create new
            NotFoundRecordEntity record = NotFoundRecordEntity.builder()
                .documentId(documentId)
                .type(type)
                .year(year)
                .number(number)
                .build();
            repository.save(record);
            log.debug("➕ Added not found: {}", documentId);
        }
    }
    
    /**
     * Vérifie si un document est marqué NOT_FOUND.
     */
    public boolean exists(String documentId) {
        return repository.existsByDocumentId(documentId);
    }
    
    /**
     * Vérifie si un document est marqué NOT_FOUND par type/year/number.
     */
    public boolean isNotFound(String type, int year, int number) {
        return repository.existsByTypeAndYearAndNumber(type, year, number);
    }
    
    /**
     * Trouve un record par documentId.
     */
    public Optional<NotFoundRecordEntity> findByDocumentId(String documentId) {
        return repository.findByDocumentId(documentId);
    }
    
    /**
     * Trouve tous les records pour un type.
     */
    public List<NotFoundRecordEntity> findByType(String type) {
        return repository.findByType(type);
    }
}
