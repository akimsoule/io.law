package bj.gouv.sgg.service;

import java.io.File;
import java.io.IOException;

/**
 * Interface pour les services d'extraction OCR.
 * 
 * Responsabilité : Extraire du texte depuis des fichiers PDF,
 * soit par extraction directe, soit par OCR si nécessaire.
 */
public interface OcrService {
    
    /**
     * Effectue l'OCR sur un fichier PDF et écrit le résultat dans un fichier texte.
     * 
     * @param pdfFile Fichier PDF source
     * @param ocrFile Fichier texte destination
     * @throws IllegalStateException si une erreur I/O survient
     */
    void performOcr(File pdfFile, File ocrFile);
    
    /**
     * Extrait le texte d'un PDF (bytes).
     * Tente d'abord l'extraction directe, puis l'OCR si la qualité est insuffisante.
     * 
     * @param pdfBytes Contenu du PDF en bytes
     * @return Texte extrait
     * @throws IOException si erreur de lecture du PDF
     */
    String extractText(byte[] pdfBytes) throws IOException;
    
    /**
     * Calcule la qualité du texte extrait.
     * 
     * @param text Texte à évaluer
     * @return Score de qualité entre 0.0 et 1.0
     */
    double calculateTextQuality(String text);
}
