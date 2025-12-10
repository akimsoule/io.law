package bj.gouv.sgg.fix.batch;

import bj.gouv.sgg.fix.model.FixResult;
import bj.gouv.sgg.fix.service.FixOrchestrator;
import bj.gouv.sgg.model.LawDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Processeur qui détecte et corrige les problèmes de chaque document.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FixProcessor implements ItemProcessor<LawDocument, LawDocument> {
    
    private final FixOrchestrator fixOrchestrator;
    
    @Override
    public LawDocument process(@NonNull LawDocument document) throws Exception {
        String docId = document.getDocumentId();
        
        log.info("🔍 [{}] Analyse document (status={})", docId, document.getStatus());
        
        // Détecter et corriger tous les problèmes
        List<FixResult> results = fixOrchestrator.detectAndFixAll(document);
        
        if (results.isEmpty()) {
            log.debug("✅ [{}] Aucune correction nécessaire", docId);
        } else {
            long successCount = results.stream()
                .filter(r -> r.getStatus() == FixResult.FixStatus.SUCCESS)
                .count();
            long failedCount = results.stream()
                .filter(r -> r.getStatus() == FixResult.FixStatus.FAILED)
                .count();
            long skippedCount = results.stream()
                .filter(r -> r.getStatus() == FixResult.FixStatus.SKIPPED)
                .count();
            
            log.info("📊 [{}] Corrections: {} succès, {} échecs, {} ignorés", 
                docId, successCount, failedCount, skippedCount);
        }
        
        return document;
    }
}
