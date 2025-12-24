package bj.gouv.sgg.batch.processor;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import bj.gouv.sgg.exception.CorruptedPdfException;
import bj.gouv.sgg.exception.OcrProcessingException;
import bj.gouv.sgg.service.FileStorageService;
import bj.gouv.sgg.service.OcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;

/**
 * ItemProcessor Spring Batch pour effectuer l'OCR des PDFs.
 * 
 * Logique:
 * 1. Vérifie que le PDF existe (pdfPath)
 * 2. Génère le chemin de destination OCR (data/ocr/{type}/{documentId}.txt)
 * 3. Effectue l'OCR via OcrService
 * 4. Met à jour l'entité : ocrPath, status=OCRED_V2
 * 5. En cas d'erreur : status=FAILED_OCR, errorMessage
 * 
 * Thread-safe : chaque thread traite son document indépendamment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrProcessor implements ItemProcessor<LawDocumentEntity, LawDocumentEntity> {
    
    private final OcrService ocrService;
    private final FileStorageService fileStorageService;
    
    @Override
    public LawDocumentEntity process(LawDocumentEntity document) {
        String documentId = document.getDocumentId();
        
        try {
            // Vérifier idempotence
            if (document.getOcrPath() != null && !document.getOcrPath().isEmpty()) {
                File ocrFile = new File(document.getOcrPath());
                if (ocrFile.exists() && ocrFile.length() > 0) {
                    log.debug("⏭️  {} déjà traité par OCR, skip", documentId);
                    return null; // Skip ce document
                }
            }
            
            // Vérifier que le PDF existe
            if (document.getPdfPath() == null || document.getPdfPath().isEmpty()) {
                log.warn("⚠️  {} n'a pas de pdfPath, skip OCR", documentId);
                return null;
            }
            
            File pdfFile = new File(document.getPdfPath());
            if (!pdfFile.exists()) {
                log.warn("⚠️  {} PDF non trouvé: {}", documentId, document.getPdfPath());
                document.setStatus(ProcessingStatus.FAILED_OCR);
                document.setErrorMessage("PDF non trouvé: " + document.getPdfPath());
                return document;
            }
            
            log.info("🔍 OCR processing {}", documentId);
            
            // Générer chemin de destination OCR
            Path ocrPath = fileStorageService.ocrPath(document.getType(), documentId);
            
            // Créer le répertoire parent si nécessaire
            ocrPath.getParent().toFile().mkdirs();
            
            // Effectuer l'OCR
            ocrService.performOcr(pdfFile, ocrPath.toFile());
            
            // Vérifier que le fichier OCR a été créé
            if (!ocrPath.toFile().exists() || ocrPath.toFile().length() == 0) {
                throw new OcrProcessingException(documentId, "Fichier OCR vide ou non créé");
            }
            
            // Mettre à jour l'entité
            document.setOcrPath(ocrPath.toString());
            document.setStatus(ProcessingStatus.OCRED_V2);
            document.setErrorMessage(null);
            
            log.info("✅ OCR completed {} ({} bytes)", documentId, ocrPath.toFile().length());
            return document;
            
        } catch (CorruptedPdfException e) {
            log.warn("⚠️  {} PDF corrompu: {}", documentId, e.getMessage());
            document.setStatus(ProcessingStatus.FAILED_CORRUPTED);
            document.setErrorMessage("PDF corrompu: " + e.getMessage());
            return document;
            
        } catch (OcrProcessingException e) {
            log.error("❌ {} OCR failed: {}", documentId, e.getMessage());
            document.setStatus(ProcessingStatus.FAILED_OCR);
            document.setErrorMessage("OCR error: " + e.getMessage());
            return document;
            
        } catch (Exception e) {
            log.error("❌ {} Unexpected error", documentId, e);
            document.setStatus(ProcessingStatus.FAILED_OCR);
            document.setErrorMessage("Unexpected: " + e.getMessage());
            return document;
        }
    }
}
