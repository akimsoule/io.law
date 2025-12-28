package bj.gouv.sgg.batch.processor;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.exception.DownloadEmptyPdfException;
import bj.gouv.sgg.exception.DownloadException;
import bj.gouv.sgg.service.FileStorageService;
import bj.gouv.sgg.service.LawDocumentValidator;
import bj.gouv.sgg.service.PdfDownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * ItemProcessor Spring Batch pour télécharger les PDFs.
 * 
 * Logique:
 * 1. Génère le chemin de destination (data/pdfs/{type}/{documentId}.pdf)
 * 2. Télécharge le PDF via PdfDownloadService
 * 3. Calcule le hash SHA-256
 * 4. Met à jour l'entité : pdfPath, pdfHash, status=DOWNLOADED
 * 5. En cas d'erreur : status=ERROR, errorMessage
 * 
 * Thread-safe : chaque thread traite son document indépendamment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DownloadProcessor implements ItemProcessor<LawDocumentEntity, LawDocumentEntity> {

    private final PdfDownloadService pdfDownloadService;
    private final FileStorageService fileStorageService;
    private final LawDocumentValidator validator;

    @Override
    public LawDocumentEntity process(LawDocumentEntity document) {
        String documentId = document.getDocumentId();

        try {
            // Vérifier idempotence (sauf si FAILED_CORRUPTED = retry)
            if (document.getStatus() != ProcessingStatus.FAILED_CORRUPTED &&
                    document.getPdfPath() != null && !document.getPdfPath().isEmpty()) {
                log.debug("⏭️  {} déjà téléchargé, skip à {}", documentId, document.getPdfPath());
                return null; // Skip ce document
            }

            if (document.getStatus() == ProcessingStatus.FAILED_CORRUPTED) {
                log.info("🔄 Retry téléchargement fichier corrompu: {}", documentId);
                // Supprimer le fichier corrompu s'il existe
                fileStorageService.deleteFileIfExists(document.getPdfPath());
            }

            // utiliser validator
            if (validator.isNotDownloaded(document)) {
                log.info("ℹ️  {} ne nécessite pas de téléchargement, skip", documentId);
                return null; // Skip ce document
            }

            // Générer chemin de destination
            Path pdfPath = fileStorageService.pdfPath(document.getType(), documentId);

            log.info("📥 Downloading {} to {}", documentId, pdfPath.toFile().getAbsolutePath());
            // Télécharger le PDF et calculer le hash
            pdfDownloadService.downloadPdf(
                    document.getType(),
                    document.getYear(),
                    document.getNumber(), // number est déjà un String
                    pdfPath);

            // Mettre à jour l'entité
            document.setPdfPath(pdfPath.toString());
            document.setStatus(ProcessingStatus.DOWNLOADED);
            document.setErrorMessage(null);

            log.info("✅ Downloaded {}", documentId);
            return document;

        } catch (DownloadEmptyPdfException e) {
            log.warn("⚠️  {} PDF vide ou corrompu: {}", documentId, e.getMessage());
            document.setStatus(ProcessingStatus.FAILED_CORRUPTED);
            document.setErrorMessage("PDF vide: " + e.getMessage());
            return document;

        } catch (DownloadException e) {
            log.error("❌ {} Download failed: {}", documentId, e.getMessage());
            document.setStatus(ProcessingStatus.FAILED_DOWNLOAD);
            document.setErrorMessage("Download error: " + e.getMessage());
            return document;

        } catch (Exception e) {
            log.error("❌ {} Unexpected error", documentId, e);
            document.setStatus(ProcessingStatus.FAILED_DOWNLOAD);
            document.setErrorMessage("Unexpected: " + e.getMessage());
            return document;
        }
    }
}
