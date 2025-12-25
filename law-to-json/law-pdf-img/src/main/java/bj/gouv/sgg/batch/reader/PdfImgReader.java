package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.repository.LawDocumentRepository;
import bj.gouv.sgg.service.LawDocumentValidator;
import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.entity.OtherProcessingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ItemReader pour trouver les documents qui ont un PDF téléchargé
 * mais n'ont pas encore de répertoire d'images généré.
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class PdfImgReader implements ItemReader<LawDocumentEntity> {

    private final LawDocumentRepository repository;
    private final LawDocumentValidator validator;
    private final AppConfig config;

    @Value("#{jobParameters['type']}")
    private String type;

    @Value("#{jobParameters['documentId']}")
    private String documentId;

    @Value("#{jobParameters['maxItems'] ?: 10}")
    private Long maxItems;

    private Queue<LawDocumentEntity> documents;

    @Override
    public synchronized LawDocumentEntity read() {
        if (documents == null) {
            initialize();
        }
        return documents.poll();
    }

    private synchronized void initialize() {
        if (documents != null) return;
        log.info("🔍 PdfImgReader - Initialisation... type={}, documentId={}", type, documentId);
        this.documents = new ConcurrentLinkedQueue<>();

        if (documentId != null && !documentId.isEmpty() && !"ALL".equals(documentId)) {
            repository.findByDocumentId(documentId).ifPresent(doc -> {
                if (shouldProcess(doc)) documents.add(doc);
            });
            log.info("📖 PdfImgReader initialisé: {} document(s)", documents.size());
            return;
        }

        List<LawDocumentEntity> found = repository.findByType(type);
        long totalFound = found.size();
        long notFoundCount = found.stream().filter(d -> d.getStatus() == ProcessingStatus.NOT_FOUND).count();
        long imagedCount = found.stream().filter(d -> d.hasOtherProcessingStatus(OtherProcessingStatus.IMAGED)).count();

        List<LawDocumentEntity> candidates = found.stream()
                .filter(d -> d.getStatus() != ProcessingStatus.NOT_FOUND)
                .filter(d -> !d.hasOtherProcessingStatus(OtherProcessingStatus.IMAGED))
                .toList();

        log.info("🔢 PdfImgReader maxItems limit = {}", maxItems);
        log.info("🔎 PdfImgReader found {} documents for type={} (total={}, excluded NOT_FOUND={}, IMAGED={})",
                candidates.size(), type, totalFound, notFoundCount, imagedCount);

        // Diagnostic: afficher les 10 premiers candidats et pourquoi ils seraient skip/accept
        int idx = 0;
        for (LawDocumentEntity d : candidates) {
            boolean pdfExists = d.getPdfPath() != null && Files.exists(Path.of(d.getPdfPath()));
            boolean imagesExist = Files.exists(config.getImagesDir().resolve(d.getDocumentId()));
            boolean imaged = d.hasOtherProcessingStatus(bj.gouv.sgg.entity.OtherProcessingStatus.IMAGED);
            boolean willProcess = shouldProcess(d);
            if (idx < 10) {
                String displayIdx = willProcess ? String.valueOf(++idx) : "-";
                log.info("  [{}] {} status={} pdfExists={} imagesExist={} imaged={} willProcess={}",
                        displayIdx, d.getDocumentId(), d.getStatus(), pdfExists, imagesExist, imaged, willProcess);
            }
        }

        for (LawDocumentEntity doc : candidates) {
            if (shouldProcess(doc)) {
                documents.add(doc);
                if (documents.size() >= (maxItems != null ? maxItems.intValue() : 10)) {
                    log.info("🔔 Reached maxItems limit ({}). Stopping enqueue.", maxItems);
                    break;
                }
            }
        }
        log.info("📖 PdfImgReader initialisé: {} document(s) à traiter (type={})", documents.size(), type);
    }

    private boolean shouldProcess(LawDocumentEntity doc) {
        // Doit être téléchargé (PDF présent) et images absentes
        if (!validator.isDownloaded(doc) && (doc.getPdfPath() == null || !Files.exists(Path.of(doc.getPdfPath())))) {
            log.debug("⏭️ Skip {} - not downloaded (no pdf on storage or pdfPath)", doc.getDocumentId());
            return false;
        }

        // Doit être non marqué IMAGED
        if (doc.hasOtherProcessingStatus(bj.gouv.sgg.entity.OtherProcessingStatus.IMAGED)) {
            log.debug("⏭️ Skip {} - already IMAGED (status present)", doc.getDocumentId());
            return false;
        }

        Path imagesDir = config.getImagesDir().resolve(doc.getDocumentId());
        if (Files.exists(imagesDir)) {
            log.debug("⏭️ Skip {} - images already exist: {}", doc.getDocumentId(), imagesDir);
            return false;
        }
        return true;
    }
}
