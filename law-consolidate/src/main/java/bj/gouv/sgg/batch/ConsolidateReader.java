package bj.gouv.sgg.batch;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.repository.LawDocumentRepository;
import bj.gouv.sgg.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

/**
 * Reader pour documents extraits (status EXTRACTED).
 * Scanne le disque pour découvrir les fichiers JSON au lieu d'utiliser la BD.
 * Vérifie que le document n'est pas déjà consolidé.
 * Thread-safe avec synchronized.
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class ConsolidateReader implements ItemReader<LawDocumentEntity> {

    private final LawDocumentRepository repository;
    private final AppConfig config;

    @Value("#{jobParameters['type']}")
    private String type;

    @Value("#{jobParameters['documentId']}")
    private String documentId;

    @Value("#{jobParameters['maxItems'] ?: 1000}")
    private Integer maxItems;

    private Queue<LawDocumentEntity> documents;

    @Override
    public synchronized LawDocumentEntity read() {
        if (documents == null) {
            initialize();
        }
        return documents.poll();
    }

    private synchronized void initialize() {
        if (documents != null)
            return;
        log.info("🔍 ConsolidateReader - Initialisation... type={}, documentId={}", type, documentId);
        this.documents = new ConcurrentLinkedQueue<>();

        try {
            if (documentId != null && !documentId.isEmpty() && !"ALL".equals(documentId)) {
                // Traiter un document spécifique
                processSpecificDocument(documentId);
            } else {
                // Scanner tous les documents EXTRACTED du type
                scanDocumentsForType(type);
            }
        } catch (Exception e) {
            log.error("❌ Erreur lors du scan pour consolidation", e);
        }

        log.info("📖 ConsolidateReader initialisé: {} document(s) à consolider", documents.size());
    }

    private void processSpecificDocument(String docId) {
        repository.findByDocumentId(docId).ifPresent(doc -> {
            if (shouldProcess(doc)) {
                documents.add(doc);
            }
        });
    }

    private void scanDocumentsForType(String type) {
        // Scanner tous les documents EXTRACTED du type
        List<LawDocumentEntity> extractedDocs = repository.findByTypeAndStatus(type, ProcessingStatus.EXTRACTED);

        log.info("🔍 Scanning {} documents with status EXTRACTED for type={}", extractedDocs.size(), type);

        int[] processedCount = { 0 };
        for (LawDocumentEntity doc : extractedDocs) {
            if (processedCount[0] >= maxItems) {
                log.info("🔔 Reached maxItems limit ({}). Stopping scan.", maxItems);
                break;
            }

            if (shouldProcess(doc)) {
                documents.add(doc);
                processedCount[0]++;
            }
        }
    }

    private boolean shouldProcess(LawDocumentEntity doc) {
        // Vérifier que le document n'a pas déjà été consolidé en BD
        if (doc.getStatus() == ProcessingStatus.CONSOLIDATED) {
            log.debug("⏭️ Skip {} - already CONSOLIDATED in BD", doc.getDocumentId());
            return false;
        }

        return true;
    }
}
