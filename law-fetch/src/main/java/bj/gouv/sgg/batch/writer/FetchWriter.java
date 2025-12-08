package bj.gouv.sgg.batch.writer;

import bj.gouv.sgg.exception.BatchProcessingException;
import bj.gouv.sgg.model.FetchResult;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchResultRepository;
import bj.gouv.sgg.repository.LawDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Writer spécialisé pour les fetch results avec pattern UPSERT.
 * Utilise findByDocumentId().orElse() + save() pour éviter les duplicate entry errors.
 * Supporte mode force pour re-traiter les documents existants.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FetchWriter implements ItemWriter<LawDocument> {
    
    private final FetchResultRepository repository;
    private final LawDocumentRepository lawDocumentRepository;
    private boolean forceMode = false;
    
    /**
     * Active le mode force (écrasement des données existantes)
     */
    public void setForceMode(boolean force) {
        this.forceMode = force;
        log.info("FetchWriter force mode: {}", force);
    }
    
    @Override
    public void write(Chunk<? extends LawDocument> chunk) throws BatchProcessingException {
        List<FetchResult> results = new ArrayList<>();
        int newCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        
        for (LawDocument document : chunk) {
            String docId = document.getDocumentId();
            Optional<FetchResult> existingOpt = repository.findByDocumentId(docId);
            boolean exists = existingOpt.isPresent();
            
            if (shouldSkipDocument(exists)) {
                skippedCount++;
                continue;
            }
            
            // ✅ UPSERT: récupérer existant ou créer nouveau
            FetchResult result = existingOpt.orElse(FetchResult.builder()
                .documentId(docId)
                .build());
            
            // Mettre à jour tous les champs
            result.setDocumentType(document.getType());
            result.setYear(document.getYear());
            result.setNumber(document.getNumber());
            result.setUrl(document.getUrl());
            result.setStatus(document.getStatus() != null ? document.getStatus().name() : "UNKNOWN");
            result.setExists(document.isExists());
            result.setFetchedAt(LocalDateTime.now());
            result.setErrorMessage(null);
            
            results.add(result);
            
            if (exists) {
                updatedCount++;
            } else {
                newCount++;
            }
            
            logDocumentProcessed(document, exists);
        }
        
        saveResults(results);
        logSummary(newCount, updatedCount, skippedCount);
    }
    
    /**
     * Vérifie si le document doit être ignoré (mode normal + existe déjà)
     */
    private boolean shouldSkipDocument(boolean exists) {
        if (!forceMode && exists) {
            log.debug("Already fetched, skipping");
            return true;
        }
        return false;
    }
    
    /**
     * Note: Les méthodes shouldDeleteExisting(), deleteExistingDocument() et createFetchResult()
     * ont été supprimées car remplacées par le pattern UPSERT dans write().
     * L'UPSERT évite les duplicate entry errors en faisant UPDATE au lieu de DELETE+INSERT.
     */
    
    /**
     * Log le traitement du document
     */
    private void logDocumentProcessed(LawDocument document, boolean exists) {
        log.debug("{} fetch: {} - Status: {} - Exists: {}", 
            forceMode && exists ? "Updated" : "New",
            document.getDocumentId(), document.getStatus(), document.isExists());
    }
    
    /**
     * Sauvegarde les résultats en batch dans fetch_results ET law_documents
     */
    private void saveResults(List<FetchResult> results) {
        if (!results.isEmpty()) {
            // Sauvegarder dans fetch_results (table de tracking)
            repository.saveAll(results);
            
            // Sauvegarder aussi dans law_documents (table principale pour downloadJob)
            List<LawDocument> documents = results.stream()
                .map(this::convertOrUpdateLawDocument)
                .toList();
            lawDocumentRepository.saveAll(documents);
        }
    }
    
    /**
     * Convertit FetchResult vers LawDocument OU met à jour document existant.
     * Évite les doublons en récupérant l'entity existante si présente (conserve l'ID).
     */
    private LawDocument convertOrUpdateLawDocument(FetchResult result) {
        // Mapper le statut de FetchResult vers LawDocument.ProcessingStatus
        LawDocument.ProcessingStatus status;
        try {
            status = LawDocument.ProcessingStatus.valueOf(result.getStatus());
        } catch (IllegalArgumentException e) {
            // Si le statut n'existe pas dans LawDocument (ex: UNKNOWN), utiliser PENDING
            log.warn("⚠️ Unknown status '{}' for document {}, defaulting to PENDING", 
                     result.getStatus(), result.getDocumentId());
            status = LawDocument.ProcessingStatus.PENDING;
        }
        
        String documentId = result.getDocumentId();
        
        // Chercher entity existante pour éviter création de doublon
        Optional<LawDocument> existingOpt = lawDocumentRepository.findByDocumentId(documentId);
        
        if (existingOpt.isPresent()) {
            // ✅ UPDATE : Mettre à jour document existant (conserve l'ID)
            LawDocument existing = existingOpt.get();
            existing.setUrl(result.getUrl());
            existing.setExists(result.getExists());
            existing.setStatus(status);
            
            log.debug("✅ Updated existing document: {}", documentId);
            return existing;
        } else {
            // ➕ INSERT : Créer nouveau document
            log.debug("➕ Creating new document: {}", documentId);
            return LawDocument.builder()
                .type(result.getDocumentType())
                .year(result.getYear())
                .number(result.getNumber())
                .url(result.getUrl())
                .exists(result.getExists())
                .status(status)
                .build();
        }
    }
    
    /**
     * Log le résumé de l'opération
     */
    private void logSummary(int newCount, int updatedCount, int skippedCount) {
        if (newCount > 0 || updatedCount > 0 || skippedCount > 0) {
            if (forceMode) {
                log.info("Saved {} fetch results ({} new, {} updated, {} skipped, FORCE mode)", 
                    newCount + updatedCount, newCount, updatedCount, skippedCount);
            } else {
                log.info("Saved {} fetch results ({} new, {} updated via UPSERT, {} skipped)", 
                    newCount + updatedCount, newCount, updatedCount, skippedCount);
            }
        }
    }
}
