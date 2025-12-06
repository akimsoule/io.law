package bj.gouv.sgg.service;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.LawDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service de gestion des statuts de traitement des documents.
 * Met à jour le statut dans l'entité LawDocument.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentStatusManager {
    
    private final LawDocumentRepository lawDocumentRepository;
    
    /**
     * Met à jour le statut d'un document.
     * 
     * @param documentId L'identifiant unique du document
     * @param newStatus Le nouveau statut (chaîne pour compatibilité)
     */
    @Transactional
    public void updateStatus(String documentId, String newStatus) {
        try {
            LawDocument.ProcessingStatus status = LawDocument.ProcessingStatus.valueOf(newStatus);
            updateStatus(documentId, status);
        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid status '{}' for document {}", newStatus, documentId);
            throw new IllegalArgumentException("Invalid status: " + newStatus, e);
        }
    }
    
    /**
     * Met à jour le statut d'un document avec l'enum ProcessingStatus.
     */
    @Transactional
    public void updateStatus(String documentId, LawDocument.ProcessingStatus newStatus) {
        String[] parts = LawDocument.parseDocumentId(documentId);
        if (parts == null) {
            log.error("❌ Invalid documentId format: {}", documentId);
            return;
        }
        
        String type = parts[0];
        int year = Integer.parseInt(parts[1]);
        int number = Integer.parseInt(parts[2]);
        
        lawDocumentRepository.findByTypeAndYearAndNumber(type, year, number).ifPresentOrElse(
            document -> {
                document.setStatus(newStatus);
                lawDocumentRepository.save(document);
                log.debug("📊 Status updated: {} -> {}", documentId, newStatus);
            },
            () -> log.warn("⚠️ Document not found: {}", documentId)
        );
    }
    
    /**
     * Enregistre une erreur pour un document.
     */
    @Transactional
    public void recordError(String documentId, String errorMessage) {
        String[] parts = LawDocument.parseDocumentId(documentId);
        if (parts == null) {
            log.error("❌ Invalid documentId format: {}", documentId);
            return;
        }
        
        String type = parts[0];
        int year = Integer.parseInt(parts[1]);
        int number = Integer.parseInt(parts[2]);
        
        lawDocumentRepository.findByTypeAndYearAndNumber(type, year, number).ifPresentOrElse(
            document -> {
                document.setStatus(LawDocument.ProcessingStatus.FAILED);
                lawDocumentRepository.save(document);
                log.warn("⚠️ Error recorded for {}: {}", documentId, errorMessage);
            },
            () -> log.error("❌ Cannot record error - document not found: {}", documentId)
        );
    }
}
