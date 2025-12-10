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
    
    private static final String INVALID_DOCUMENT_ID_MSG = "❌ Invalid documentId format: {}";
    
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
            updateStatusInternal(documentId, status);
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
        updateStatusInternal(documentId, newStatus);
    }
    
    /**
     * Méthode interne pour mettre à jour le statut (évite appel transactionnel via this).
     */
    private void updateStatusInternal(String documentId, LawDocument.ProcessingStatus newStatus) {
        String[] parts = LawDocument.parseDocumentId(documentId);
        if (parts.length == 0) {
            log.error(INVALID_DOCUMENT_ID_MSG, documentId);
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
     * Met à jour le statut de plusieurs documents en lot.
     */
    @Transactional
    public void bulkUpdateStatus(LawDocument.ProcessingStatus fromStatus, LawDocument.ProcessingStatus toStatus) {
        var documents = lawDocumentRepository.findByStatus(fromStatus);
        documents.forEach(doc -> doc.setStatus(toStatus));
        lawDocumentRepository.saveAll(documents);
        log.info("📊 Bulk status update: {} documents {} -> {}", documents.size(), fromStatus, toStatus);
    }
    
    /**
     * Enregistre une erreur pour un document.
     */
    @Transactional
    public void recordError(String documentId, String errorMessage) {
        String[] parts = LawDocument.parseDocumentId(documentId);
        if (parts.length == 0) {
            log.error(INVALID_DOCUMENT_ID_MSG, documentId);
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
