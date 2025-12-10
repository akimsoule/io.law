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
        
        // Charger tous les documents (pagination pour mémoire)
        Pageable pageable = PageRequest.of(0, 1000);
        Page<LawDocument> page = lawDocumentRepository.findAll(pageable);
        
        long totalDocuments = page.getTotalElements();
        log.info("📄 {} documents à analyser", totalDocuments);
        
        documentIterator = page.getContent().iterator();
        
        // Log distribution par statut
        lawDocumentRepository.findAll().stream()
            .collect(java.util.stream.Collectors.groupingBy(
                LawDocument::getStatus,
                java.util.stream.Collectors.counting()
            ))
            .forEach((status, count) -> 
                log.info("   {} : {} documents", status, count)
            );
    }
}
