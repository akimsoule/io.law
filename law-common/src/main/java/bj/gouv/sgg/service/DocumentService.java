package bj.gouv.sgg.service;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.model.DocumentRecord;
import bj.gouv.sgg.model.ProcessingStatus;
import bj.gouv.sgg.storage.JsonStorage;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service de gestion des documents sans Spring Data JPA.
 * Utilise JsonStorage pour la persistance.
 */
@Slf4j
public class DocumentService {
    
    private final JsonStorage<DocumentRecord> storage;
    private final FileStorageService fileStorageService;
    
    public DocumentService() {
        AppConfig config = AppConfig.get();
        this.storage = new JsonStorage<>(
            config.getDocumentsPath(),
            new TypeToken<List<DocumentRecord>>() {}
        );
        this.fileStorageService = new FileStorageService();
    }
    
    public DocumentService(Path storagePath, FileStorageService fileStorageService) {
        this.storage = new JsonStorage<>(storagePath, new TypeToken<List<DocumentRecord>>() {});
        this.fileStorageService = fileStorageService;
    }
    
    public void save(DocumentRecord document) {
        document.setUpdatedAt(LocalDateTime.now());
        
        Optional<DocumentRecord> existing = findByDocumentId(document.getDocumentId());
        if (existing.isPresent()) {
            storage.update(
                d -> d.getDocumentId().equals(document.getDocumentId()),
                d -> document
            );
        } else {
            if (document.getCreatedAt() == null) {
                document.setCreatedAt(LocalDateTime.now());
            }
            storage.append(document);
        }
    }
    
    public void saveAll(List<DocumentRecord> documents) {
        documents.forEach(this::save);
    }
    
    public Optional<DocumentRecord> findByDocumentId(String documentId) {
        return storage.findFirst(d -> d.getDocumentId().equals(documentId));
    }
    
    public Optional<DocumentRecord> findByTypeYearNumber(String type, int year, int number) {
        String docId = String.format("%s-%d-%d", type, year, number);
        return findByDocumentId(docId);
    }
    
    public List<DocumentRecord> findByStatus(ProcessingStatus status) {
        return storage.findAll(d -> d.getStatus() == status);
    }
    
    public List<DocumentRecord> findByTypeAndStatus(String type, ProcessingStatus status) {
        return storage.findAll(d -> 
            d.getType().equals(type) && d.getStatus() == status
        );
    }
    
    public long countByStatus(ProcessingStatus status) {
        return storage.count(d -> d.getStatus() == status);
    }
    
    public long countByTypeAndStatus(String type, ProcessingStatus status) {
        return storage.count(d -> 
            d.getType().equals(type) && d.getStatus() == status
        );
    }
    
    public List<DocumentRecord> findAll() {
        return storage.readAll();
    }
    
    public void updateStatus(String documentId, ProcessingStatus status) {
        storage.update(
            d -> d.getDocumentId().equals(documentId),
            d -> {
                d.setStatus(status);
                d.setUpdatedAt(LocalDateTime.now());
                return d;
            }
        );
    }
    
    public void updateStatusWithPaths(String documentId, ProcessingStatus status,
                                     String pdfPath, String ocrPath, String jsonPath) {
        storage.update(
            d -> d.getDocumentId().equals(documentId),
            d -> {
                d.setStatus(status);
                if (pdfPath != null) d.setPdfPath(pdfPath);
                if (ocrPath != null) d.setOcrPath(ocrPath);
                if (jsonPath != null) d.setJsonPath(jsonPath);
                d.setUpdatedAt(LocalDateTime.now());
                return d;
            }
        );
    }
    
    public void setError(String documentId, String errorMessage) {
        storage.update(
            d -> d.getDocumentId().equals(documentId),
            d -> {
                d.setStatus(ProcessingStatus.FAILED);
                d.setErrorMessage(errorMessage);
                d.setUpdatedAt(LocalDateTime.now());
                return d;
            }
        );
    }
    
    public boolean exists(String documentId) {
        return findByDocumentId(documentId).isPresent();
    }
    
    public void deleteAll() {
        storage.writeAll(List.of());
    }
}
