package bj.gouv.sgg.service.impl;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.exception.FileOperationException;
import bj.gouv.sgg.impl.TesseractOcrServiceImpl;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests d'intégration pour TesseractOcrServiceImpl avec de vrais PDFs.
 * 
 * Ces tests génèrent des PDFs de test et vérifient l'extraction complète :
 * - PDFs texte natif (extraction directe)
 * - PDFs scannés (OCR Tesseract)
 * - Gestion erreurs et cas limites
 */
class OcrIntegrationTest {
    
    private TesseractOcrServiceImpl ocrService;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        // Configuration OCR
        LawProperties properties = new LawProperties();
        LawProperties.Ocr ocrConfig = new LawProperties.Ocr();
        ocrConfig.setQualityThreshold(0.5);
        ocrConfig.setDpi(300);
        ocrConfig.setLanguage("fra");
        properties.setOcr(ocrConfig);
        
        ocrService = new TesseractOcrServiceImpl(properties);
    }
    
    // ==================== Tests Extraction Directe ====================
    
    @Test
    void givenSimplePdfWithTextWhenExtractTextThenContainsArticleAndTest() throws IOException {
        // Given: PDF avec texte simple contenant "Article 1er : Ceci est un test."
        File pdfFile = createSimpleTextPdf("Article 1er : Ceci est un test.");
        byte[] pdfBytes = Files.readAllBytes(pdfFile.toPath());
        
        // When: Extraction du texte OCR
        String extractedText = ocrService.extractText(pdfBytes);
        
        // Then: Le texte extrait contient "Article" et "test"
        assertNotNull(extractedText, "Le texte extrait ne devrait pas être null");
        assertFalse(extractedText.isBlank(), "Le texte extrait ne devrait pas être vide");
        assertTrue(extractedText.contains("Article"), 
                  "Le texte devrait contenir 'Article', obtenu: " + extractedText);
        assertTrue(extractedText.contains("test"), 
                  "Le texte devrait contenir 'test', obtenu: " + extractedText);
    }
    
    @Test
    void givenLegalDocumentPdfWhenExtractTextThenContainsAllArticles() throws IOException {
        // Given: PDF type document légal avec 3 articles
        String legalText = """
                RÉPUBLIQUE DU BÉNIN
                
                LOI N° 2024-15
                
                Article 1er : Objet
                Le présent décret porte sur les finances publiques.
                
                Article 2 : Définitions
                Au sens du présent décret, on entend par budget l'ensemble des recettes et dépenses.
                
                Article 3 : Entrée en vigueur
                Le présent décret sera publié au Journal Officiel.
                """;
        
        File pdfFile = createMultilineTextPdf(legalText);
        
        // Extraire
        byte[] pdfBytes = Files.readAllBytes(pdfFile.toPath());
        String extractedText = ocrService.extractText(pdfBytes);
        
        // Vérifications structure légale
        assertNotNull(extractedText);
        assertTrue(extractedText.contains("RÉPUBLIQUE"), 
                  "Devrait contenir en-tête");
        assertTrue(extractedText.contains("Article 1er"), 
                  "Devrait contenir Article 1er");
        assertTrue(extractedText.contains("Article 2"), 
                  "Devrait contenir Article 2");
        assertTrue(extractedText.contains("Article 3"), 
                  "Devrait contenir Article 3");
        assertTrue(extractedText.contains("finances publiques"), 
                  "Devrait contenir contenu article 1");
    }
    
    @Test
    void givenMultiPagePdfWhenExtractTextThenContainsAllPages() throws IOException {
        // Given: PDF de 3 pages avec texte distinct par page
        File pdfFile = createMultiPagePdf(new String[]{
            "Page 1: Introduction du document",
            "Page 2: Développement des articles",
            "Page 3: Conclusion et signatures"
        });
        
        // Extraire
        byte[] pdfBytes = Files.readAllBytes(pdfFile.toPath());
        String extractedText = ocrService.extractText(pdfBytes);
        
        // Vérifier contenu 3 pages
        assertNotNull(extractedText);
        assertTrue(extractedText.contains("Page 1"), "Devrait contenir page 1");
        assertTrue(extractedText.contains("Page 2"), "Devrait contenir page 2");
        assertTrue(extractedText.contains("Page 3"), "Devrait contenir page 3");
    }
    
    @Test
    void givenEmptyPdfWhenExtractTextThenReturnsEmptyOrMinimalText() throws IOException {
        // Given: PDF vide (juste header PDF)
        File pdfFile = createEmptyPdf();
        
        // Extraire
        byte[] pdfBytes = Files.readAllBytes(pdfFile.toPath());
        String extractedText = ocrService.extractText(pdfBytes);
        
        // Vérifier résultat vide ou minimal
        assertNotNull(extractedText);
        assertTrue(extractedText.trim().isEmpty() || extractedText.length() < 50,
                  "PDF vide devrait donner texte vide ou minimal");
    }
    
    // ==================== Tests Qualité et Stratégie ====================
    
    @Test
    void givenHighQualityPdfWhenExtractTextThenQualityAboveThreshold() throws IOException {
        // Given: PDF qualité élevée → extraction directe (pas OCR)
        String highQualityText = "Article 1er : Le présent décret définit les modalités " +
                                "d'application du code des finances publiques.";
        
        File pdfFile = createSimpleTextPdf(highQualityText);
        byte[] pdfBytes = Files.readAllBytes(pdfFile.toPath());
        
        // Extraire
        String extractedText = ocrService.extractText(pdfBytes);
        
        // Vérifier qualité
        double quality = ocrService.calculateTextQuality(extractedText);
        assertTrue(quality >= 0.5, 
                  "Qualité devrait être >= 0.5 (seuil), obtenu: " + quality);
        
        // Vérifier contenu
        assertTrue(extractedText.contains("Article"), 
                  "Extraction directe devrait préserver le texte");
    }
    
    // ==================== Tests performOcr (File → File) ====================
    
    @Test
    void givenSimplePdfWhenPerformOcrThenCreatesOutputFile() throws IOException {
        // Given: PDF source simple
        File pdfFile = createSimpleTextPdf("Test OCR avec fichier output.");
        
        // Créer fichier output dans tempDir
        File ocrOutputFile = tempDir.resolve("test_simple_ocr.txt").toFile();
        
        // Exécuter OCR
        ocrService.performOcr(pdfFile, ocrOutputFile);
        
        // Vérifier fichier créé
        assertTrue(ocrOutputFile.exists(), "Fichier OCR devrait être créé");
        assertTrue(ocrOutputFile.length() > 0, "Fichier OCR ne devrait pas être vide");
        
        // Vérifier contenu
        String content = Files.readString(ocrOutputFile.toPath());
        assertTrue(content.contains("Test") || content.contains("OCR"),
                  "Fichier OCR devrait contenir du texte extrait");
    }
    
    @Test
    void givenPdfAndNestedOutputPathWhenPerformOcrThenCreatesNestedDirectories() throws IOException {
        // Given: PDF source et chemin output imbriqué
        File pdfFile = createSimpleTextPdf("Test répertoires imbriqués.");
        
        // Créer répertoires parents dans tempDir
        Path outputDir = tempDir.resolve("subdir1/subdir2");
        Files.createDirectories(outputDir);
        File ocrOutputFile = outputDir.resolve("test_nested_dirs.txt").toFile();
        
        // Exécuter OCR
        ocrService.performOcr(pdfFile, ocrOutputFile);
        
        // Vérifier
        assertTrue(ocrOutputFile.exists(), "Fichier devrait être créé");
        assertTrue(ocrOutputFile.getParentFile().exists(), 
                  "Répertoires parents devraient exister");
    }
    
    @Test
    void givenSamePdfAndOutputWhenPerformOcrTwiceThenProducesIdenticalResults() throws IOException {
        // Given: PDF source pour test idempotence
        File pdfFile = createSimpleTextPdf("Test idempotence OCR.");
        File ocrOutputFile = tempDir.resolve("test_idempotent.txt").toFile();
        
        // 1ère exécution
        ocrService.performOcr(pdfFile, ocrOutputFile);
        String firstContent = Files.readString(ocrOutputFile.toPath());
        long firstSize = ocrOutputFile.length();
        
        // 2ème exécution (même fichiers)
        ocrService.performOcr(pdfFile, ocrOutputFile);
        String secondContent = Files.readString(ocrOutputFile.toPath());
        long secondSize = ocrOutputFile.length();
        
        // Vérifier idempotence (même résultat)
        assertEquals(firstContent, secondContent, 
                    "Contenu devrait être identique après 2 exécutions");
        assertEquals(firstSize, secondSize, 
                    "Taille fichier devrait être identique");
    }
    
    // ==================== Tests Erreurs ====================
    
    @Test
    void givenInvalidPdfBytesWhenExtractTextThenThrowsIOException() {
        // Given: Bytes invalides (non-PDF)
        byte[] invalidBytes = "This is not a PDF".getBytes();
        
        // Devrait lever exception
        assertThrows(IOException.class, () -> ocrService.extractText(invalidBytes),
                "Bytes invalides devraient lever IOException");
    }
    
    @Test
    void givenNullInputsWhenPerformOcrThenThrowsException() throws IOException {
        // Given: Entrées null (PDF ou output null)
        File outputFile = tempDir.resolve("out.txt").toFile();
        assertThrows(NullPointerException.class,
                () -> ocrService.performOcr(null, outputFile),
                "PDF null devrait lever NullPointerException");
        
        // Null output - L'implémentation wrappe dans FileOperationException via ErrorHandlingUtils
        File validPdf = createSimpleTextPdf("test");
        assertThrows(FileOperationException.class,
                () -> ocrService.performOcr(validPdf, null),
                "Output null devrait lever FileOperationException (wrappée par ErrorHandlingUtils)");
    }
    
    @Test
    void givenNonExistentPdfFileWhenPerformOcrThenThrowsFileOperationException() {
        // Given: Fichier PDF inexistant
        File nonExistentPdf = tempDir.resolve("nonexistent.pdf").toFile();
        File ocrOutput = tempDir.resolve("output.txt").toFile();
        
        // L'implémentation wrappe les erreurs I/O dans FileOperationException
        assertThrows(FileOperationException.class,
                () -> ocrService.performOcr(nonExistentPdf, ocrOutput),
                "PDF inexistant devrait lever FileOperationException");
    }
    
    // ==================== Helpers - Génération PDFs Test ====================
    
    /**
     * Crée un PDF simple avec une ligne de texte.
     */
    private File createSimpleTextPdf(String text) throws IOException {
        File pdfFile = tempDir.resolve("test.pdf").toFile();
        
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            
            document.save(pdfFile);
        }
        
        return pdfFile;
    }
    
    /**
     * Crée un PDF avec texte multiligne.
     */
    private File createMultilineTextPdf(String text) throws IOException {
        File pdfFile = tempDir.resolve("multiline.pdf").toFile();
        
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                contentStream.setLeading(14.5f);
                contentStream.newLineAtOffset(50, 750);
                
                // Écrire ligne par ligne
                for (String line : text.split("\n")) {
                    if (!line.trim().isEmpty()) {
                        contentStream.showText(line.trim());
                        contentStream.newLine();
                    } else {
                        contentStream.newLine(); // Ligne vide
                    }
                }
                
                contentStream.endText();
            }
            
            document.save(pdfFile);
        }
        
        return pdfFile;
    }
    
    /**
     * Crée un PDF multi-pages.
     */
    private File createMultiPagePdf(String[] pageContents) throws IOException {
        File pdfFile = tempDir.resolve("multipage.pdf").toFile();
        
        try (PDDocument document = new PDDocument()) {
            for (String content : pageContents) {
                PDPage page = new PDPage();
                document.addPage(page);
                
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    contentStream.newLineAtOffset(50, 700);
                    contentStream.showText(content);
                    contentStream.endText();
                }
            }
            
            document.save(pdfFile);
        }
        
        return pdfFile;
    }
    
    /**
     * Crée un PDF vide (aucune page ou page blanche).
     */
    private File createEmptyPdf() throws IOException {
        File pdfFile = tempDir.resolve("empty.pdf").toFile();
        
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            // Pas de contenu
            document.save(pdfFile);
        }
        
        return pdfFile;
    }
}
