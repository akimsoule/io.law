package bj.gouv.sgg.service;

import bj.gouv.sgg.config.LawProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service de gestion du stockage des fichiers (PDF, OCR, JSON).
 * Fournit les chemins normalisés et les opérations de lecture/écriture.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {
    
    private final LawProperties lawProperties;
    
    /**
     * Obtient le chemin vers le fichier PDF d'un document.
     * 
     * @param type Le type du document ("loi" ou "decret")
     * @param documentId L'identifiant unique du document (ex: "loi-2024-15")
     * @return Le chemin complet vers le fichier PDF
     */
    public Path pdfPath(String type, String documentId) {
        validateType(type);
        validateDocumentId(documentId);
        
        return Paths.get(lawProperties.getDirectories().getData())
                .resolve(lawProperties.getDirectories().getPdfs())
                .resolve(type)
                .resolve(documentId + ".pdf");
    }
    
    /**
     * Obtient le chemin vers le fichier OCR d'un document.
     */
    public Path ocrPath(String type, String documentId) {
        validateType(type);
        validateDocumentId(documentId);
        
        return Paths.get(lawProperties.getDirectories().getData())
                .resolve(lawProperties.getDirectories().getOcr())
                .resolve(type)
                .resolve(documentId + ".txt");
    }
    
    /**
     * Obtient le chemin vers le fichier JSON d'un document.
     */
    public Path jsonPath(String type, String documentId) {
        validateType(type);
        validateDocumentId(documentId);
        
        return Paths.get(lawProperties.getDirectories().getData())
                .resolve(lawProperties.getDirectories().getArticles())
                .resolve(type)
                .resolve(documentId + ".json");
    }
    
    /**
     * Vérifie si le PDF d'un document existe.
     */
    public boolean pdfExists(String type, String documentId) {
        return Files.exists(pdfPath(type, documentId));
    }
    
    /**
     * Vérifie si le fichier OCR d'un document existe.
     */
    public boolean ocrExists(String type, String documentId) {
        return Files.exists(ocrPath(type, documentId));
    }
    
    /**
     * Vérifie si le fichier JSON d'un document existe.
     */
    public boolean jsonExists(String type, String documentId) {
        return Files.exists(jsonPath(type, documentId));
    }
    
    /**
     * Sauvegarde un PDF sur disque.
     * 
     * @param type Le type du document
     * @param documentId L'identifiant du document
     * @param pdfContent Le contenu binaire du PDF
     * @throws IOException Si une erreur d'écriture survient
     */
    public void savePdf(String type, String documentId, byte[] pdfContent) throws IOException {
        Path path = pdfPath(type, documentId);
        Files.createDirectories(path.getParent());
        Files.write(path, pdfContent);
        log.debug("✅ PDF saved: {}", path);
    }
    
    /**
     * Sauvegarde un fichier OCR sur disque.
     */
    public void saveOcr(String type, String documentId, String ocrContent) throws IOException {
        Path path = ocrPath(type, documentId);
        Files.createDirectories(path.getParent());
        Files.writeString(path, ocrContent);
        log.debug("✅ OCR saved: {}", path);
    }
    
    /**
     * Sauvegarde un fichier JSON sur disque.
     */
    public void saveJson(String type, String documentId, String jsonContent) throws IOException {
        Path path = jsonPath(type, documentId);
        Files.createDirectories(path.getParent());
        Files.writeString(path, jsonContent);
        log.debug("✅ JSON saved: {}", path);
    }
    
    /**
     * Lit le contenu d'un PDF.
     */
    public byte[] readPdf(String type, String documentId) throws IOException {
        return Files.readAllBytes(pdfPath(type, documentId));
    }
    
    /**
     * Lit le contenu d'un fichier OCR.
     */
    public String readOcr(String type, String documentId) throws IOException {
        return Files.readString(ocrPath(type, documentId));
    }
    
    /**
     * Lit le contenu d'un fichier JSON.
     */
    public String readJson(String type, String documentId) throws IOException {
        return Files.readString(jsonPath(type, documentId));
    }
    
    /**
     * Valide le type de document.
     */
    private void validateType(String type) {
        if (type == null || (!type.equals("loi") && !type.equals("decret"))) {
            throw new IllegalArgumentException("Invalid document type: " + type);
        }
    }
    
    /**
     * Valide l'identifiant de document (sécurité path traversal).
     */
    private void validateDocumentId(String documentId) {
        if (documentId == null || documentId.contains("..") || documentId.contains("/") || documentId.contains("\\")) {
            throw new SecurityException("Invalid document ID: " + documentId);
        }
    }
}
