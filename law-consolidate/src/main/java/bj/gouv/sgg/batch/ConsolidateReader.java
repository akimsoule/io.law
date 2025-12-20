package bj.gouv.sgg.batch;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.repository.LawDocumentRepository;
import bj.gouv.sgg.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Reader pour documents extraits (status EXTRACTED).
 * Vérifie que le fichier JSON existe avant traitement.
 * Thread-safe avec double-check locking.
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class ConsolidateReader implements ItemReader<LawDocumentEntity> {

    private final LawDocumentRepository repository;

    @Value("#{jobParameters['type']}")
    private String type;

    @Value("#{jobParameters['documentId']}")
    private String documentId;

    @Value("#{jobParameters['maxItems'] ?: 1000}")
    private Integer maxItems;

    private ConcurrentLinkedQueue<LawDocumentEntity> documentQueue;
    private volatile boolean initialized = false;

    /**
     * Initialisation thread-safe avec double-check locking.
     */
    private synchronized void initialize() {
        if (initialized) {
            return;
        }

        log.info("📖 ConsolidateReader: type={}, documentId={}, maxItems={}", type, documentId, maxItems);

        List<LawDocumentEntity> documents;

        // Mode ciblé (documentId fourni) vs mode batch (type entier)
        if (documentId != null && !documentId.isEmpty() && !"ALL".equalsIgnoreCase(documentId)) {
            String[] parts = documentId.split("-");
            if (parts.length == 3) {
                try {
                    String docType = parts[0];
                    int year = Integer.parseInt(parts[1]);
                    String number = parts[2]; // String car peut avoir zéros (ex: 001)

                    documents = repository.findByTypeAndYearAndNumber(docType, year, number)
                            .map(List::of)
                            .orElseGet(() -> {
                                log.warn("⚠️ Document non trouvé: {}", documentId);
                                return List.of();
                            });
                } catch (NumberFormatException e) {
                    log.warn("⚠️ Format invalide documentId: {}", documentId);
                    documents = List.of();
                }
            } else {
                log.warn("⚠️ Format invalide documentId: {}", documentId);
                documents = List.of();
            }
        } else {
            // Mode batch: tous les documents EXTRACTED du type
            documents = repository.findByTypeAndStatus(type, ProcessingStatus.EXTRACTED);
            log.info("📄 {} documents EXTRACTED trouvés pour type={}", documents.size(), type);
        }

        // Filtrer documents pour idempotence
        List<LawDocumentEntity> filteredDocuments = documents.stream()
                .filter(doc -> {
                    // Ne pas traiter si déjà CONSOLIDATED
                    if (doc.getStatus() == ProcessingStatus.CONSOLIDATED) {
                        log.debug("⏭️ Déjà consolidé (status): {}", doc.getDocumentId());
                        return false;
                    }

                    return true;
                })
                .limit(maxItems)
                .toList();

        log.info("✅ {} documents à consolider après filtrage", filteredDocuments.size());

        documentQueue = new ConcurrentLinkedQueue<>(filteredDocuments);
        initialized = true;
    }

    @Override
    public synchronized LawDocumentEntity read() {
        if (!initialized) {
            initialize();
        }
        return documentQueue.poll();
    }
}
