package bj.gouv.sgg.service;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Service de validation des documents de loi avec vérification du système de
 * fichiers.
 *
 * <p>
 * Ce service combine la logique métier basée sur le status de l'entité
 * avec la vérification de l'existence réelle des fichiers sur le disque.
 *
 * @author io.law
 * @since 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LawDocumentValidator {

    private final AppConfig config;

    @PostConstruct
    public void init() {
        log.info("✅ LawDocumentValidator initialized with Spring");
    }

    // ========== FETCH VALIDATION ==========

    /**
     * Vérifie si le document a été fetch.
     * Retourne true si le status est dans la chaîne de traitement OU si des
     * fichiers existent.
     */
    public boolean isFetched(LawDocumentEntity entity) {
        ProcessingStatus status = entity.getStatus();

        // Si le status indique que le document a été fetch
        if (status == ProcessingStatus.FETCHED ||
                status == ProcessingStatus.NOT_FOUND ||
                status == ProcessingStatus.DOWNLOADED ||
                status == ProcessingStatus.OCRED_V2 ||
                status == ProcessingStatus.EXTRACTED ||
                status == ProcessingStatus.CONSOLIDATED) {
            return true;
        }

        // OU si des fichiers existent sur le disque (preuve qu'il a été fetch)
        if (pdfExists(entity) || ocrExists(entity) || jsonExists(entity)) {
            return true;
        }

        return false;
    }

    // ========== DOWNLOAD VALIDATION ==========


    /**
     * Vérifie si le document a été téléchargé.
     * Combine le status avec la vérification de l'existence du PDF sur le disque.
     */
    public boolean isNotDownloaded(LawDocumentEntity entity) {
        ProcessingStatus status = entity.getStatus();
        boolean isDownloaded = pdfExists(entity);

        boolean downloadedIsStored = status == ProcessingStatus.DOWNLOADED || status == ProcessingStatus.OCRED_V2
                || status == ProcessingStatus.EXTRACTED || status == ProcessingStatus.CONSOLIDATED;

        return !(isDownloaded || downloadedIsStored);
    }

    // ========== OCR VALIDATION ==========

    /**
     * Vérifie si le document doit subir l'OCR.
     * Combine le status avec la vérification de l'existence du fichier OCR.
     */
    public boolean mustOcr(LawDocumentEntity entity) {
        // Cas 1: Status indique qu'il faut faire l'OCR
        if (entity.getStatus() == ProcessingStatus.DOWNLOADED) {
            return true;
        }

        // Cas 2: Échec OCR précédent
        if (entity.getStatus() == ProcessingStatus.FAILED_OCR) {
            return true;
        }

        // Cas 3: Status indique "ocred" mais fichier OCR absent
        if (entity.getStatus() == ProcessingStatus.OCRED_V2 && !ocrExists(entity)) {
            log.warn("⚠️ Document {} marqué OCRED_V2 mais fichier OCR absent", entity.getDocumentId());
            return true;
        }

        return false;
    }

    /**
     * Vérifie si l'OCR a été effectué.
     * Combine le status avec la vérification de l'existence du fichier OCR.
     */
    public boolean isOcred(LawDocumentEntity entity) {
        ProcessingStatus status = entity.getStatus();

        boolean statusOk = status == ProcessingStatus.OCRED_V2 || status == ProcessingStatus.EXTRACTED
                || status == ProcessingStatus.CONSOLIDATED;

        // Si le status indique OCR fait, vérifier que le fichier existe vraiment
        if (statusOk) {
            return ocrExists(entity);
        }

        return false;
    }

    // ========== EXTRACTION VALIDATION ==========

    /**
     * Vérifie si les articles doivent être extraits.
     * Combine le status avec la vérification de l'existence du JSON.
     */
    public boolean mustExtractArticles(LawDocumentEntity entity) {
        // Cas 1: Status indique qu'il faut extraire (après OCR)
        if (entity.getStatus() == ProcessingStatus.OCRED_V2) {
            return true;
        }

        // Cas 2: Échec extraction précédent
        if (entity.getStatus() == ProcessingStatus.FAILED_EXTRACTION) {
            return true;
        }

        return false;
    }

    /**
     * Vérifie si les articles ont été extraits.
     * Combine le status avec la vérification de l'existence du JSON.
     */
    public boolean isExtracted(LawDocumentEntity entity) {
        ProcessingStatus status = entity.getStatus();

        boolean statusOk = status == ProcessingStatus.EXTRACTED || status == ProcessingStatus.CONSOLIDATED;

        // Si le status indique extrait, vérifier que le fichier JSON existe vraiment
        if (statusOk) {
            return jsonExists(entity);
        }

        return false;
    }

    // ========== CONSOLIDATION VALIDATION ==========

    /**
     * Vérifie si le document doit être consolidé.
     */
    public boolean mustConsolidate(LawDocumentEntity entity) {
        return entity.getStatus() == ProcessingStatus.EXTRACTED;
    }

    /**
     * Vérifie si le document a été consolidé.
     */
    public boolean isConsolidated(LawDocumentEntity entity) {
        return entity.getStatus() == ProcessingStatus.CONSOLIDATED;
    }

    // ========== FILE EXISTENCE CHECKS ==========

    /**
     * Vérifie si le fichier PDF existe sur le disque.
     */
    public boolean pdfExists(LawDocumentEntity entity) {
        Path pdfPath = config.getStoragePath().resolve("pdfs").resolve(entity.getType())
                .resolve(entity.getDocumentId() + ".pdf");
        return Files.exists(pdfPath);
    }

    /**
     * Vérifie si le fichier OCR existe sur le disque.
     */
    public boolean ocrExists(LawDocumentEntity entity) {
        Path ocrPath = config.getStoragePath().resolve("ocr").resolve(entity.getType())
                .resolve(entity.getDocumentId() + ".txt");
        return Files.exists(ocrPath);
    }

    /**
     * Vérifie si le fichier JSON existe sur le disque.
     */
    public boolean jsonExists(LawDocumentEntity entity) {
        Path jsonPath = config.getStoragePath().resolve("articles").resolve(entity.getType())
                .resolve(entity.getDocumentId() + ".json");
        return Files.exists(jsonPath);
    }

    /**
     * Retourne le chemin du fichier PDF.
     */
    public Path getPdfPath(LawDocumentEntity entity) {
        return config.getStoragePath().resolve("pdfs").resolve(entity.getType())
                .resolve(entity.getDocumentId() + ".pdf");
    }

    /**
     * Retourne le chemin du fichier OCR.
     */
    public Path getOcrPath(LawDocumentEntity entity) {
        return config.getStoragePath().resolve("ocr").resolve(entity.getType())
                .resolve(entity.getDocumentId() + ".txt");
    }

    /**
     * Retourne le chemin du fichier JSON.
     */
    public Path getJsonPath(LawDocumentEntity entity) {
        return config.getStoragePath().resolve("articles").resolve(entity.getType())
                .resolve(entity.getDocumentId() + ".json");
    }
}
