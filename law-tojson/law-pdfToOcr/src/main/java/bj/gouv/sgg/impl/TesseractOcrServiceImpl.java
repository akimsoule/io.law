package bj.gouv.sgg.impl;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.exception.TesseractInitializationException;
import bj.gouv.sgg.service.OcrService;
import bj.gouv.sgg.util.ErrorHandlingUtils;
import bj.gouv.sgg.util.FileExistenceHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.leptonica.PIX;
import org.bytedeco.tesseract.TessBaseAPI;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.bytedeco.leptonica.global.leptonica.*;

/**
 * Implémentation Tesseract du service OCR.
 * 
 * Stratégie :
 * 1. Tentative extraction directe du PDF (texte natif)
 * 2. Si qualité < seuil → OCR avec Tesseract
 * 3. Support multi-pages avec détection "AMPLIATIONS" (arrêt)
 * 
 * Clean Code :
 * - Pas de null returns (Optional ou chaîne vide)
 * - Exceptions spécifiques (TesseractInitializationException)
 * - try-with-resources pour gestion ressources
 * - Logs structurés avec contexte
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TesseractOcrServiceImpl implements OcrService {
    
    private final LawProperties properties;
    
    // Répertoire temporaire pour tessdata (extrait une seule fois)
    private static Path tessdataDir;
    
    @Override
    public void performOcr(File pdfFile, File ocrFile) {
        ErrorHandlingUtils.executeVoid(() -> {
            try {
                byte[] pdfBytes = Files.readAllBytes(pdfFile.toPath());
                String text = extractText(pdfBytes);
                FileExistenceHelper.ensureExists(ocrFile.toPath().getParent(), "Ensure OCR output dir");
                Files.writeString(ocrFile.toPath(), text);
                log.info("✅ OCR completed: {} -> {} ({} chars)", 
                         pdfFile.getName(), ocrFile.getName(), text.length());
            } catch (IOException | bj.gouv.sgg.exception.OcrProcessingException e) {
                throw new IllegalStateException("I/O error during OCR for " + pdfFile, e);
            }
        }, "performOcr", pdfFile.getName());
    }
    
    @Override
    public String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            // Tentative extraction directe
            PDFTextStripper stripper = new PDFTextStripper();
            String directText = stripper.getText(document);
            
            double quality = calculateTextQuality(directText);
            
            if (quality >= properties.getOcr().getQualityThreshold()) {
                log.info("✅ Direct extraction OK (quality: {:.2f})", quality);
                return directText;
            }
            
            log.info("🔄 Direct extraction quality too low ({:.2f}), using OCR", quality);
            return extractWithOcr(document);
        }
    }
    
    @Override
    public double calculateTextQuality(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0.0;
        }
        
        int totalChars = text.length();
        int validChars = 0;
        int spaces = 0;
        
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                validChars++;
            } else if (Character.isWhitespace(c)) {
                spaces++;
            }
        }
        
        double validRatio = (double) validChars / totalChars;
        double spaceRatio = (double) spaces / totalChars;
        
        // Texte de bonne qualité : beaucoup de caractères valides + espacement raisonnable
        return (validRatio * 0.7) + (Math.min(spaceRatio, 0.2) * 1.5);
    }
    
    /**
     * Extrait et prépare les données Tesseract depuis les resources.
     * Les fichiers .traineddata doivent être dans src/main/resources/tessdata/
     */
    private static synchronized Path extractTessdata() throws IOException {
        if (tessdataDir == null) {
            tessdataDir = Files.createTempDirectory("tessdata");
            String[] files = { "fra.traineddata" }; // Français uniquement
            
            for (String file : files) {
                try (InputStream is = TesseractOcrServiceImpl.class.getResourceAsStream("/tessdata/" + file)) {
                    if (is != null) {
                        Path targetFile = tessdataDir.resolve(file);
                        Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                        log.debug("📦 Tesseract tessdata extracted: {}", file);
                    } else {
                        log.warn("⚠️ Tesseract tessdata not found in resources: {}", file);
                    }
                }
            }
        }
        return tessdataDir;
    }
    
    private String extractWithOcr(PDDocument document) throws IOException {
        StringBuilder result = new StringBuilder();
        PDFRenderer renderer = new PDFRenderer(document);
        int totalPages = document.getNumberOfPages();
        
        log.info("🔄 OCR processing {} pages", totalPages);
        
        // Extraire tessdata dans un répertoire temporaire
        Path tessDir = extractTessdata();
        
        try (TessBaseAPI api = new TessBaseAPI()) {
            initializeTesseract(api, tessDir);
            
            for (int page = 0; page < totalPages; page++) {
                String pageText = processPage(api, renderer, page);
                
                if (pageText != null && !pageText.isBlank()) {
                    if (totalPages > 1) {
                        result.append("%n%n=== Page ".formatted(page + 1))
                              .append("/").append(totalPages).append(" ===%n%n");
                    }
                    result.append(pageText);
                    
                    // Détection "AMPLIATIONS" → fin du document légal
                    if (pageText.toUpperCase().contains("AMPLIATIONS")) {
                        log.info("🛑 AMPLIATIONS detected at page {}/{} (stopping OCR)", 
                                 page + 1, totalPages);
                        break;
                    }
                }
                
                // Log progression (chaque 10 pages)
                if ((page + 1) % 10 == 0 || page == totalPages - 1) {
                    log.info("📊 OCR progress: {}/{} pages", page + 1, totalPages);
                }
            }
        }
        
        return result.toString();
    }
    
    /**
     * Initialise Tesseract avec retry en cas d'échec.
     */
    private void initializeTesseract(TessBaseAPI api, Path tessDir) {
        int maxRetries = 3;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            if (api.Init(tessDir.toString(), properties.getOcr().getLanguage()) == 0) {
                log.debug("✅ Tesseract initialized (attempt {})", attempt);
                return;
            }
            
            if (attempt < maxRetries) {
                log.warn("⚠️ Tesseract initialization failed, retry {}/{}", attempt, maxRetries);
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new TesseractInitializationException(
                        tessDir.toString(), 
                        "Tesseract initialization interrupted", 
                        e
                    );
                }
            }
        }
        
        throw new TesseractInitializationException(tessDir.toString(), maxRetries);
    }
    
    /**
     * Traite une page PDF individuelle : conversion en image puis OCR.
     */
    private String processPage(TessBaseAPI api, PDFRenderer renderer, int pageIndex) throws IOException {
        BufferedImage image = renderer.renderImageWithDPI(
            pageIndex, 
            properties.getOcr().getDpi()
        );
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();
        
        PIX pix = pixReadMem(imageBytes, imageBytes.length);
        if (pix == null) {
            log.warn("⚠️ Failed to read image for page {}", pageIndex);
            return "";
        }
        
        try {
            api.SetImage(pix);
            BytePointer textPtr = api.GetUTF8Text();
            if (textPtr != null) {
                try {
                    return textPtr.getString(StandardCharsets.UTF_8);
                } finally {
                    textPtr.deallocate();
                }
            }
            return "";
        } finally {
            pixDestroy(pix);
        }
    }
}
