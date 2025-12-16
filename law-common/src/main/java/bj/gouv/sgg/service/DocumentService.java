package bj.gouv.sgg.service;

import bj.gouv.sgg.config.DatabaseConfig;
import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.model.DocumentRecord;
import bj.gouv.sgg.model.ProcessingStatus;
import bj.gouv.sgg.repository.LawDocumentRepository;
import bj.gouv.sgg.repository.impl.JpaLawDocumentRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service de gestion des documents avec MySQL/JPA.
 * Remplace JsonStorage pour garantir l'intégrité des données.
 */
@Slf4j
public class DocumentService {
    
    private final LawDocumentRepository repository;
    private final EntityManager entityManager;
    private final FileStorageService fileStorageService;
    
    public DocumentService() {
        DatabaseConfig dbConfig = DatabaseConfig.getInstance();
        this.entityManager = dbConfig.createEntityManager();
        this.repository = new JpaLawDocumentRepository(entityManager);
        this.fileStorageService = new FileStorageService();
        
        log.info("✅ DocumentService initialized with MySQL backend");
    }
    
    /**
     * Sauvegarde un document (insert ou update).
     * 
     * @param record Le document à sauvegarder
     * @return Le document sauvegardé
     */
    public DocumentRecord save(DocumentRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        
        if (record.getType() == null || record.getType().isEmpty()) {
            throw new IllegalArgumentException("Document type cannot be null or empty");
        }
        
        // Convertir DocumentRecord → LawDocumentEntity
        LawDocumentEntity entity = toEntity(record);
        
        // Sauvegarder en base
        entity = repository.save(entity);
        
        // Retourner le record mis à jour
        return toRecord(entity);
    }
    
    /**
     * Sauvegarde plusieurs documents en batch.
     */
    public List<DocumentRecord> saveAll(List<DocumentRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        
        List<LawDocumentEntity> entities = records.stream()
            .map(this::toEntity)
            .collect(Collectors.toList());
        
        entities = repository.saveAll(entities);
        
        return entities.stream()
            .map(this::toRecord)
            .collect(Collectors.toList());
    }
    
    /**
     * Trouve un document par son identifiant métier (ex: "loi-2024-15").
     * 
     * @param documentId L'identifiant du document
     * @return Optional contenant le document si trouvé
     */
    public Optional<DocumentRecord> findByDocumentId(String documentId) {
        if (documentId == null || documentId.isEmpty()) {
            return Optional.empty();
        }
        
        return repository.findByDocumentId(documentId)
            .map(this::toRecord);
    }
    
    /**
     * Trouve un document par type, année et numéro.
     */
    public Optional<DocumentRecord> findByTypeAndYearAndNumber(String type, int year, int number) {
        if (type == null || type.isEmpty()) {
            return Optional.empty();
        }
        
        return repository.findByTypeAndYearAndNumber(type, year, number)
            .map(this::toRecord);
    }
    
    /**
     * Vérifie l'existence d'un document.
     */
    public boolean exists(String documentId) {
        return repository.existsByDocumentId(documentId);
    }
    
    /**
     * Vérifie l'existence d'un document par type, année et numéro.
     */
    public boolean exists(String type, int year, int number) {
        return repository.existsByTypeAndYearAndNumber(type, year, number);
    }
    
    /**
     * Trouve tous les documents avec un statut donné.
     */
    public List<DocumentRecord> findByStatus(ProcessingStatus status) {
        if (status == null) {
            return List.of();
        }
        
        return repository.findByStatus(status).stream()
            .map(this::toRecord)
            .collect(Collectors.toList());
    }
    
    /**
     * Trouve tous les documents d'un type et année donnés.
     */
    public List<DocumentRecord> findByTypeAndYear(String type, int year) {
        if (type == null || type.isEmpty()) {
            return List.of();
        }
        
        return repository.findByTypeAndYear(type, year).stream()
            .map(this::toRecord)
            .collect(Collectors.toList());
    }
    
    /**
     * Trouve tous les documents d'un type avec un statut donné.
     */
    public List<DocumentRecord> findByTypeAndStatus(String type, ProcessingStatus status) {
        if (type == null || type.isEmpty() || status == null) {
            return List.of();
        }
        
        return repository.findByStatus(status).stream()
            .filter(entity -> type.equals(entity.getType()))
            .map(this::toRecord)
            .collect(Collectors.toList());
    }
    
    /**
     * Compte les documents avec un statut donné.
     */
    public long countByStatus(ProcessingStatus status) {
        return repository.countByStatus(status);
    }
    
    /**
     * Trouve tous les documents.
     */
    public List<DocumentRecord> findAll() {
        return repository.findAll().stream()
            .map(this::toRecord)
            .collect(Collectors.toList());
    }
    
    /**
     * Met à jour le statut d'un document.
     */
    public void updateStatus(String documentId, ProcessingStatus newStatus) {
        Optional<LawDocumentEntity> optEntity = repository.findByDocumentId(documentId);
        
        if (optEntity.isEmpty()) {
            log.warn("⚠️ Document not found for status update: {}", documentId);
            return;
        }
        
        LawDocumentEntity entity = optEntity.get();
        ProcessingStatus oldStatus = entity.getStatus();
        entity.setStatus(newStatus);
        entity.setUpdatedAt(LocalDateTime.now());
        
        repository.save(entity);
        log.info("✅ Status updated: {} ({} → {})", documentId, oldStatus, newStatus);
    }
    
    /**
     * Supprime un document.
     */
    public void delete(String documentId) {
        repository.findByDocumentId(documentId)
            .ifPresent(repository::delete);
    }
    
    /**
     * Supprime tous les documents.
     */
    public void deleteAll() {
        repository.deleteAll();
    }
    
    /**
     * Ferme l'EntityManager (à appeler au shutdown).
     */
    public void close() {
        if (entityManager != null && entityManager.isOpen()) {
            entityManager.close();
            log.info("✅ DocumentService closed");
        }
    }
    
    // ========== CONVERSION HELPERS ==========
    
    /**
     * Convertit DocumentRecord → LawDocumentEntity.
     */
    private LawDocumentEntity toEntity(DocumentRecord record) {
        LawDocumentEntity entity = new LawDocumentEntity();
        
        // Chercher si le document existe déjà
        Optional<LawDocumentEntity> existing = repository.findByTypeAndYearAndNumber(
            record.getType(), record.getYear(), record.getNumber()
        );
        
        if (existing.isPresent()) {
            entity = existing.get();
        }
        
        entity.setType(record.getType());
        entity.setYear(record.getYear());
        entity.setNumber(record.getNumber());
        entity.setStatus(record.getStatus());
        entity.setUrl(record.getUrl());
        entity.setTitle(record.getTitle());
        entity.setPdfPath(record.getPdfPath());
        entity.setOcrPath(record.getOcrPath());
        entity.setJsonPath(record.getJsonPath());
        entity.setErrorMessage(record.getErrorMessage());
        entity.setCreatedAt(record.getCreatedAt());
        entity.setUpdatedAt(LocalDateTime.now());
        
        return entity;
    }
    
    /**
     * Convertit LawDocumentEntity → DocumentRecord.
     */
    private DocumentRecord toRecord(LawDocumentEntity entity) {
        DocumentRecord record = new DocumentRecord();
        
        record.setType(entity.getType());
        record.setYear(entity.getYear());
        record.setNumber(entity.getNumber());
        record.setStatus(entity.getStatus());
        record.setUrl(entity.getUrl());
        record.setTitle(entity.getTitle());
        record.setPdfPath(entity.getPdfPath());
        record.setOcrPath(entity.getOcrPath());
        record.setJsonPath(entity.getJsonPath());
        record.setErrorMessage(entity.getErrorMessage());
        record.setCreatedAt(entity.getCreatedAt());
        record.setUpdatedAt(entity.getUpdatedAt());
        
        return record;
    }
}
