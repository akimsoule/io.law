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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

/**
 * ItemReader pour trouver les documents qui ont un PDF téléchargé
 * mais n'ont pas encore de répertoire d'images généré.
 * 
 * Scanne le disque pour découvrir les PDFs au lieu d'utiliser la BD.
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
        if (documents != null)
            return;
        log.info("🔍 PdfImgReader - Initialisation... type={}, documentId={}", type, documentId);
        this.documents = new ConcurrentLinkedQueue<>();

        try {
            if (documentId != null && !documentId.isEmpty() && !"ALL".equals(documentId)) {
                // Traiter un document spécifique
                processSpecificDocument(documentId);
            } else {
                // Scanner tous les PDFs du type
                scanPdfsForType(type);
            }
        } catch (IOException e) {
            log.error("❌ Erreur lors du scan du disque", e);
        }

        log.info("📖 PdfImgReader initialisé: {} document(s) à traiter", documents.size());
    }

    private void processSpecificDocument(String docId) throws IOException {
        Path pdfDir = config.getStoragePath().resolve("pdfs").resolve(type);
        Path pdfFile = pdfDir.resolve(docId + ".pdf");

        if (Files.exists(pdfFile)) {
            repository.findByDocumentId(docId).ifPresent(doc -> {
                if (shouldProcess(doc)) {
                    documents.add(doc);
                }
            });
        } else {
            log.warn("⚠️ PDF non trouvé pour documentId={} : {}", docId, pdfFile);
        }
    }

    private void scanPdfsForType(String type) throws IOException {
        Path pdfDir = config.getStoragePath().resolve("pdfs").resolve(type);

        if (!Files.exists(pdfDir)) {
            log.warn("⚠️ Répertoire PDF non trouvé: {}", pdfDir);
            return;
        }

        log.info("🔍 Scanning PDFs in {}", pdfDir);

        try (Stream<Path> paths = Files.list(pdfDir)) {
            List<Path> pdfFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".pdf"))
                    .sorted() // Pour cohérence
                    .toList();

            log.info("🔎 Trouvé {} fichiers PDF pour type={}", pdfFiles.size(), type);

            int processed = 0;
            for (Path pdfFile : pdfFiles) {
                String fileName = pdfFile.getFileName().toString();
                String docId = fileName.substring(0, fileName.length() - 4); // Enlever .pdf

                repository.findByDocumentId(docId).ifPresentOrElse(doc -> {
                    boolean willProcess = shouldProcess(doc);
                    if (willProcess) {
                        documents.add(doc);
                    }
                }, () -> {
                    log.debug("⏭️ Skip {} - document non trouvé en BD", docId);
                });

                if (documents.size() >= (maxItems != null ? maxItems.intValue() : 10)) {
                    log.info("🔔 Reached maxItems limit ({}). Stopping scan.", maxItems);
                    break;
                }
            }
        }
    }

    private boolean shouldProcess(LawDocumentEntity doc) {
        // Vérifier que le PDF existe (normalement oui puisque scanné)
        if (!validator.pdfExists(doc)) {
            log.debug("⏭️ Skip {} - PDF non trouvé sur disque", doc.getDocumentId());
            return false;
        }

        // Doit être non marqué IMAGED
        if (doc.hasOtherProcessingStatus(OtherProcessingStatus.IMAGED)) {
            log.debug("⏭️ Skip {} - already IMAGED", doc.getDocumentId());
            return false;
        }

        // Vérifier que les images n'existent pas
        Path imagesDir = config.getImagesDir().resolve(doc.getDocumentId());
        if (Files.exists(imagesDir)) {
            log.debug("⏭️ Skip {} - images already exist: {}", doc.getDocumentId(), imagesDir);
            return false;
        }

        return true;
    }
}
