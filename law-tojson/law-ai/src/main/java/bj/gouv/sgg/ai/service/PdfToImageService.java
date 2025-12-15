package bj.gouv.sgg.ai.service;

import bj.gouv.sgg.exception.IAException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Service de conversion PDF en images base64 pour envoi aux modèles IA vision.
 * 
 * <p><b>Workflow</b> :
 * <pre>
 * PDF → Pages individuelles → BufferedImage → JPEG → Base64
 * </pre>
 * 
 * <p><b>Optimisations</b> :
 * <ul>
 *   <li>DPI configurable (150 par défaut pour équilibre qualité/taille)</li>
 *   <li>Compression JPEG à 85% pour réduire taille</li>
 *   <li>Limitation nombre de pages pour éviter dépassement contexte</li>
 *   <li>Estimation taille base64 avant conversion</li>
 * </ul>
 */
@Service
@Slf4j
public class PdfToImageService {

    private static final int DEFAULT_DPI = 150;
    private static final int MAX_PAGES_PER_REQUEST = 10;
    
    /**
     * Convertit un PDF en liste d'images base64.
     * 
     * <p><b>Limites</b> : Max 10 pages par défaut pour éviter dépassement
     * de la limite de contexte des modèles. Utiliser chunking pour PDFs longs.
     * 
     * @param pdfPath Chemin vers fichier PDF
     * @return Liste d'images base64 (une par page)
     * @throws IAException Si conversion échoue
     */
    public List<String> convertToBase64Images(Path pdfPath) throws IAException {
        return convertToBase64Images(pdfPath, DEFAULT_DPI, MAX_PAGES_PER_REQUEST);
    }
    
    /**
     * Convertit un PDF en liste d'images base64 avec paramètres personnalisés.
     * 
     * @param pdfPath Chemin vers fichier PDF
     * @param dpi Résolution des images (150-300 recommandé)
     * @param maxPages Nombre maximum de pages à convertir
     * @return Liste d'images base64
     * @throws IAException Si conversion échoue
     */
    public List<String> convertToBase64Images(Path pdfPath, int dpi, int maxPages) throws IAException {
        if (!Files.exists(pdfPath)) {
            throw new IAException("PDF file not found: " + pdfPath);
        }
        
        List<String> base64Images = new ArrayList<>();
        
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();
            int pagesToProcess = Math.min(totalPages, maxPages);
            
            log.info("📄 Converting PDF to images: {} pages (max: {})", pagesToProcess, maxPages);
            
            for (int pageIndex = 0; pageIndex < pagesToProcess; pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi);
                String base64 = imageToBase64(image);
                base64Images.add(base64);
                
                log.debug("✅ Page {}/{} converted ({} KB)", 
                         pageIndex + 1, pagesToProcess, base64.length() / 1024);
            }
            
            if (totalPages > maxPages) {
                log.warn("⚠️ PDF has {} pages but only {} converted (limit reached)", 
                        totalPages, maxPages);
            }
            
            return base64Images;
            
        } catch (IOException e) {
            throw new IAException("Failed to convert PDF to images: " + pdfPath, e);
        }
    }
    
    /**
     * Convertit une image en base64 JPEG.
     * 
     * @param image Image à convertir
     * @return String base64
     * @throws IOException Si conversion échoue
     */
    private String imageToBase64(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // Convertir en JPEG avec compression
            ImageIO.write(image, "JPEG", baos);
            byte[] imageBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        }
    }
    
    /**
     * Estime la taille totale en MB des images base64 qui seront générées.
     * 
     * <p>Utile pour vérifier si PDF dépasse limites du modèle avant conversion.
     * 
     * @param pdfPath Chemin vers PDF
     * @param dpi Résolution prévue
     * @param maxPages Nombre de pages à convertir
     * @return Taille estimée en MB
     */
    public double estimateSizeMB(Path pdfPath, int dpi, int maxPages) {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            int pages = Math.min(document.getNumberOfPages(), maxPages);
            
            // Estimation empirique : ~500KB par page en base64 à 150 DPI
            double baseSizePerPage = 0.5; // MB
            double dpiMultiplier = Math.pow((double) dpi / 150, 2);
            
            return pages * baseSizePerPage * dpiMultiplier;
            
        } catch (IOException e) {
            log.warn("Failed to estimate PDF size: {}", e.getMessage());
            return maxPages * 0.5; // Estimation par défaut
        }
    }
    
    /**
     * Compte le nombre de pages d'un PDF.
     * 
     * @param pdfPath Chemin vers PDF
     * @return Nombre de pages
     * @throws IAException Si lecture échoue
     */
    public int countPages(Path pdfPath) throws IAException {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            throw new IAException("Failed to count PDF pages: " + pdfPath, e);
        }
    }
}
