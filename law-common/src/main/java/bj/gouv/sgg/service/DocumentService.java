package bj.gouv.sgg.service;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.repository.LawDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service de gestion des documents avec Spring Data JPA.
 * Injection automatique du repository par Spring.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class DocumentService {
    
    private final LawDocumentRepository repository;
    
    @PostConstruct
    public void init() {
        log.info("✅ DocumentService initialized with Spring Data JPA");
    }
    
    /**
     * Sauvegarde un document (insert ou update).
     * 
     * @param document Le document à sauvegarder
     * @return Le document sauvegardé
     */
    public LawDocumentEntity save(LawDocumentEntity document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        
        if (document.getType() == null || document.getType().isEmpty()) {
            throw new IllegalArgumentException("Document type cannot be null or empty");
        }
        
        return repository.save(document);
    }
    
    /**
     * Sauvegarde plusieurs documents en batch.
     */
    public List<LawDocumentEntity> saveAll(List<LawDocumentEntity> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        
        return repository.saveAll(documents);
    }
    
    /**
     * Trouve un document par son identifiant métier (ex: "loi-2024-15").
     * 
     * @param documentId L'identifiant du document
     * @return Optional contenant le document si trouvé
     */
    public Optional<LawDocumentEntity> findByDocumentId(String documentId) {
        if (documentId == null || documentId.isEmpty()) {
            return Optional.empty();
        }
        
        return repository.findByDocumentId(documentId);
    }
    
    /**
     * Trouve un document par type, année et numéro.
     */
    public Optional<LawDocumentEntity> findByTypeAndYearAndNumber(String type, int year, String number) {
        if (type == null || type.isEmpty()) {
            return Optional.empty();
        }
        
        return repository.findByTypeAndYearAndNumber(type, year, number);
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
    public boolean exists(String type, int year, String number) {
        return repository.existsByTypeAndYearAndNumber(type, year, number);
    }
    
    /**
     * Trouve tous les documents avec un statut donné.
     */
    public List<LawDocumentEntity> findByStatus(ProcessingStatus status) {
        if (status == null) {
            return List.of();
        }
        
        return repository.findByStatus(status);
    }
    
    /**
     * Trouve tous les documents d'un type et année donnés.
     */
    public List<LawDocumentEntity> findByTypeAndYear(String type, int year) {
        if (type == null || type.isEmpty()) {
            return List.of();
        }
        
        return repository.findByTypeAndYear(type, year);
    }
    
    /**
     * Trouve tous les documents d'un type avec un statut donné.
     */
    public List<LawDocumentEntity> findByTypeAndStatus(String type, ProcessingStatus status) {
        if (type == null || type.isEmpty() || status == null) {
            return List.of();
        }
        
        return repository.findByStatus(status).stream()
            .filter(entity -> type.equals(entity.getType()))
            .collect(Collectors.toList());
    }

    /**
     * Trouve tous les documents d'un type, année et statut donnés.
     */
    public List<LawDocumentEntity> findByTypeAndYearAndStatus(String type, int year, ProcessingStatus status) {
        if (type == null || type.isEmpty() || status == null) {
            return List.of();
        }
        
        return repository.findByTypeAndYear(type, year).stream()
            .filter(entity -> status.equals(entity.getStatus()))
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
    public List<LawDocumentEntity> findAll() {
        return repository.findAll();
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
}
