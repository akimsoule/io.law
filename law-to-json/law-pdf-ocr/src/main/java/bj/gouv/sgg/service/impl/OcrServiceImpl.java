package bj.gouv.sgg.service.impl;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.exception.CorruptedPdfException;
import bj.gouv.sgg.exception.TesseractInitializationException;
import bj.gouv.sgg.service.OcrService;
import bj.gouv.sgg.util.FileExistenceHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.leptonica.PIX;
import org.bytedeco.tesseract.TessBaseAPI;

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
 * - Pattern Singleton pour éviter réinstanciations Tesseract
 * - Logs structurés avec contexte
 */
@Slf4j
public class OcrServiceImpl implements OcrService {
    
    private static OcrServiceImpl instance;
    private final AppConfig config;
    
    // Répertoire temporaire pour tessdata (extrait une seule fois)
    private static Path tessdataDir;
    
    private OcrServiceImpl(AppConfig config) {
        this.config = config;
    }
    
    public static synchronized OcrServiceImpl getInstance() {
        if (instance == null) {
            instance = new OcrServiceImpl(AppConfig.get());
        }
        return instance;
    }
    
    @Override
    public void performOcr(File pdfFile, File ocrFile) {
        String documentId = pdfFile.getName().replace(".pdf", "");
        
        try {
            // Éviter readAllBytes pour économiser mémoire (Raspberry Pi)
            String text = extractTextFromFile(pdfFile.toPath());
            FileExistenceHelper.ensureExists(ocrFile.toPath().getParent(), "Ensure OCR output dir");
            Files.writeString(ocrFile.toPath(), text);
            
            log.info("✅ OCR completed: {} -> {} ({} chars)", 
                     pdfFile.getName(), ocrFile.getName(), text.length());
        } catch (CorruptedPdfException e) {
            log.error("🚨 PDF corrompu: {}", pdfFile.getName());
            throw e; // Remonter pour traitement spécifique
        } catch (IOException | bj.gouv.sgg.exception.OcrProcessingException e) {
            log.error("❌ OCR failed for {}: {}", pdfFile.getName(), e.getMessage());
            throw new IllegalStateException("I/O error during OCR for " + pdfFile, e);
        }
    }
    
    private String extractTextFromFile(Path pdfPath) throws IOException {
        try {
            try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
                return extractTextFromDocument(document, pdfPath.getFileName().toString());
            }
        } catch (IOException e) {
            // Détecter PDF corrompu via messages d'erreur PDFBox
            String errorMsg = e.getMessage();
            if (errorMsg != null && (
                errorMsg.contains("Missing root object") ||
                errorMsg.contains("Header doesn't contain versioninfo") ||
                errorMsg.contains("expected='endobj'") ||
                errorMsg.contains("COSStream has been closed") ||
                errorMsg.contains("Error: Expected a long type")
            )) {
                String documentId = pdfPath.getFileName().toString().replace(".pdf", "");
                throw new CorruptedPdfException(documentId, "PDF corrompu: " + errorMsg, e);
            }
            throw e;
        }
    }
    
    @Override
    public String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return extractTextFromDocument(document, "in-memory");
        }
    }
    
    private String extractTextFromDocument(PDDocument document, String filename) throws IOException {
        int totalPages = document.getNumberOfPages();
        log.debug("📄 {} has {} pages", filename, totalPages);
            
            // Tentative extraction directe de toutes les pages
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(totalPages);
            String directText = stripper.getText(document);
            
            log.debug("📝 Direct extraction: {} chars from {} pages", 
                     directText.length(), totalPages);
            
            double quality = calculateTextQuality(directText);
            
            // Vérifier si la première page contient du texte
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String firstPageText = stripper.getText(document);
            boolean firstPageHasText = firstPageText != null && firstPageText.trim().length() > 50;
            
            if (quality >= config.getOcrQualityThreshold() && firstPageHasText) {
                log.info("✅ Direct extraction OK (quality: {:.2f}, first page OK)", quality);
                return directText;
            }
            
            if (!firstPageHasText) {
                log.info("🔄 First page has no text ({} chars), using OCR", 
                         firstPageText == null ? 0 : firstPageText.trim().length());
            } else {
                log.info("🔄 Direct extraction quality too low ({:.2f}), using OCR", quality);
            }
            
            return extractWithOcr(document, filename);
    }
    
    /**
     * Calcule la qualité d'un texte extrait.
     */
    @Override
    public double calculateTextQuality(String text) {
        if (text == null || text.isEmpty()) {
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
                try (InputStream is = OcrServiceImpl.class.getResourceAsStream("/tessdata/" + file)) {
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
    
    private String extractWithOcr(PDDocument document, String filename) throws IOException {
        Path tessDir = extractTessdata();
        StringBuilder sb = new StringBuilder(4096);
        
        try (TessBaseAPI api = new TessBaseAPI()) {
            
            // Initialiser Tesseract
            if (api.Init(tessDir.toString(), config.getOcrLanguage()) != 0) {
                throw new IOException("Failed to initialize Tesseract (datapath=" + tessDir + ")");
            }
            
            api.SetPageSegMode(org.bytedeco.tesseract.global.tesseract.PSM_AUTO);
            
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            
            log.info("🔄 OCR processing: {} ({} pages)", filename, pageCount);
            
            for (int i = 0; i < pageCount; i++) {
                try {
                    String pageText = processPage(api, renderer, i, pageCount, filename);
                    
                    if (pageText != null && !pageText.isBlank()) {
                        if (pageCount > 1) {
                            sb.append("\n\n=== Page ").append(i + 1)
                              .append("/").append(pageCount).append(" ===\n\n");
                        }
                        sb.append(pageText);
                        
                        // Vérifier si AMPLIATIONS est détecté (fin de la loi)
                        if (pageText.toUpperCase().contains("AMPLIATIONS")) {
                            log.info("🛑 AMPLIATIONS detected at page {}/{} (stopping OCR)", 
                                     i + 1, pageCount);
                            break;
                        }
                    }
                    
                    // Libérer mémoire régulièrement (Raspberry Pi)
                    if ((i + 1) % 5 == 0) {
                        System.gc();
                    }
                    
                } catch (IOException e) {
                    log.warn("⚠️ Failed to OCR page {}/{}: {}", i + 1, pageCount, e.getMessage());
                }
                
                // Log de progression
                if ((i + 1) % 10 == 0 || i == pageCount - 1) {
                    log.info("📊 OCR progress: {}/{} pages", i + 1, pageCount);
                }
            }
            
            // Cleanup Tesseract explicitement
            api.End();
        }
        
        return sb.toString().trim();
    }
    
    /**
     * Traite une page PDF (optimisé mémoire pour Raspberry Pi).
     */
    private String processPage(TessBaseAPI api, PDFRenderer renderer, int pageIndex, int totalPages, String filename) throws IOException {
        BufferedImage image = null;
        ByteArrayOutputStream baos = null;
        PIX pix = null;
        BytePointer textPtr = null;
        
        try {
            // DPI adaptatif si Raspberry Pi
            int dpi = config.getOcrDpi();
            if (config.getCapacity() != null && config.getCapacity().getOcr() <= 2) {
                dpi = Math.min(dpi, 200); // Réduire sur Raspberry Pi
            }
            
            image = renderer.renderImageWithDPI(pageIndex, dpi);
            
            baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();
            
            pix = pixReadMem(imageBytes, imageBytes.length);
            if (pix == null) {
                log.warn("⚠️ {} page {}/{}: Failed to read image", filename, pageIndex + 1, totalPages);
                return "";
            }
            
            api.SetImage(pix);
            textPtr = api.GetUTF8Text();
            if (textPtr != null) {
                return textPtr.getString(StandardCharsets.UTF_8);
            }
            return "";
            
        } finally {
            // Libération explicite mémoire (critique sur Raspberry Pi)
            if (textPtr != null) {
                textPtr.deallocate();
            }
            if (pix != null) {
                pixDestroy(pix);
            }
            if (baos != null) {
                baos.close();
            }
        }
    }
}
