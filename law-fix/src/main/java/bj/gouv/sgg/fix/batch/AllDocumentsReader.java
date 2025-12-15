package bj.gouv.sgg.fix.batch;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.LawDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Iterator;

/**
 * Reader qui lit TOUS les documents (tous statuts) pour détection d'issues.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AllDocumentsReader implements ItemReader<LawDocument> {
    
    private final LawDocumentRepository lawDocumentRepository;
    
    private Iterator<LawDocument> documentIterator;
    private boolean initialized = false;
    
    @Nullable
    @Override
    public LawDocument read() {
        if (!initialized) {
            initialize();
            initialized = true;
        }
        
        if (documentIterator != null && documentIterator.hasNext()) {
            return documentIterator.next();
        }
        
        return null; // Fin du reader
    }
    
    private void initialize() {
        log.info("🔍 Chargement de tous les documents pour analyse...");
        
        // Charger tous les documents SAUF FAILED (404 permanents, non corrigibles)
        Pageable pageable = PageRequest.of(0, 1000);
        Page<LawDocument> page = lawDocumentRepository.findAll(pageable);
        
        // Filtrer les documents FAILED
        java.util.List<LawDocument> documents = page.getContent().stream()
            .filter(doc -> doc.getStatus() != LawDocument.ProcessingStatus.FAILED)
            .toList();
        
        long totalDocuments = documents.size();
        long excludedFailed = page.getTotalElements() - totalDocuments;
        
        log.info("📄 {} documents à analyser ({} FAILED exclus)", totalDocuments, excludedFailed);
        
        documentIterator = documents.iterator();
        
        // Log distribution par statut (hors FAILED)
        documents.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                LawDocument::getStatus,
                java.util.stream.Collectors.counting()
            ))
            .forEach((status, count) -> 
                log.info("   {} : {} documents", status, count)
            );
    }
}
