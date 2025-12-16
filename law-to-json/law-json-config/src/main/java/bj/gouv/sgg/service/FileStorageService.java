package bj.gouv.sgg.service;

import bj.gouv.sgg.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Service de gestion des fichiers (PDFs, OCR, JSON).
 * 
 * <p>Fournit des méthodes pour lire/écrire les fichiers
 * selon la structure de stockage configurée.
 */
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {
    
    private final AppConfig config;
    
    /**
     * Lit le contenu OCR d'un document.
     * 
     * @param type Type de document (loi/decret)
     * @param documentId ID du document (ex: loi-2024-15)
     * @return Contenu du fichier OCR
     * @throws IOException Si le fichier n'existe pas ou erreur de lecture
     */
    public String readOcr(String type, String documentId) throws IOException {
        Path ocrPath = getOcrPath(type, documentId);
        
        if (!Files.exists(ocrPath)) {
            throw new IOException("Fichier OCR introuvable: " + ocrPath);
        }
        
        return Files.readString(ocrPath);
    }
    
    /**
     * Écrit le contenu OCR d'un document.
     * 
     * @param type Type de document (loi/decret)
     * @param documentId ID du document (ex: loi-2024-15)
     * @param content Contenu OCR à écrire
     * @throws IOException Si erreur d'écriture
     */
    public void writeOcr(String type, String documentId, String content) throws IOException {
        Path ocrPath = getOcrPath(type, documentId);
        Files.createDirectories(ocrPath.getParent());
        Files.writeString(ocrPath, content);
    }
    
    /**
     * Lit le contenu JSON d'un document.
     * 
     * @param type Type de document (loi/decret)
     * @param documentId ID du document (ex: loi-2024-15)
     * @return Contenu du fichier JSON
     * @throws IOException Si le fichier n'existe pas ou erreur de lecture
     */
    public String readJson(String type, String documentId) throws IOException {
        Path jsonPath = getJsonPath(type, documentId);
        
        if (!Files.exists(jsonPath)) {
            throw new IOException("Fichier JSON introuvable: " + jsonPath);
        }
        
        return Files.readString(jsonPath);
    }
    
    /**
     * Écrit le contenu JSON d'un document.
     * 
     * @param type Type de document (loi/decret)
     * @param documentId ID du document (ex: loi-2024-15)
     * @param content Contenu JSON à écrire
     * @throws IOException Si erreur d'écriture
     */
    public void writeJson(String type, String documentId, String content) throws IOException {
        Path jsonPath = getJsonPath(type, documentId);
        Files.createDirectories(jsonPath.getParent());
        Files.writeString(jsonPath, content);
    }
    
    /**
     * Obtient le chemin du fichier PDF.
     * 
     * @param type Type de document (loi/decret)
     * @param documentId ID du document (ex: loi-2024-15)
     * @return Chemin du fichier PDF
     */
    public Path getPdfPath(String type, String documentId) {
        return config.getPdfDir()
            .resolve(type)
            .resolve(documentId + ".pdf");
    }
    
    /**
     * Obtient le chemin du fichier OCR.
     * 
     * @param type Type de document (loi/decret)
     * @param documentId ID du document (ex: loi-2024-15)
     * @return Chemin du fichier OCR
     */
    public Path getOcrPath(String type, String documentId) {
        return config.getOcrDir()
            .resolve(type)
            .resolve(documentId + ".txt");
    }
    
    /**
     * Obtient le chemin du fichier JSON.
     * 
     * @param type Type de document (loi/decret)
     * @param documentId ID du document (ex: loi-2024-15)
     * @return Chemin du fichier JSON
     */
    public Path getJsonPath(String type, String documentId) {
        return config.getJsonDir()
            .resolve(type)
            .resolve(documentId + ".json");
    }
    
    /**
     * Vérifie si un fichier PDF existe.
     * 
     * @param type Type de document (loi/decret)
     * @param documentId ID du document (ex: loi-2024-15)
     * @return true si le fichier existe, false sinon
     */
    public boolean pdfExists(String type, String documentId) {
        return Files.exists(getPdfPath(type, documentId));
    }
    
    /**
     * Vérifie si un fichier OCR existe.
     * 
     * @param type Type de document (loi/decret)
     * @param documentId ID du document (ex: loi-2024-15)
     * @return true si le fichier existe, false sinon
     */
    public boolean ocrExists(String type, String documentId) {
        return Files.exists(getOcrPath(type, documentId));
    }
    
    /**
     * Vérifie si un fichier JSON existe.
     * 
     * @param type Type de document (loi/decret)
     * @param documentId ID du document (ex: loi-2024-15)
     * @return true si le fichier existe, false sinon
     */
    public boolean jsonExists(String type, String documentId) {
        return Files.exists(getJsonPath(type, documentId));
    }
}
