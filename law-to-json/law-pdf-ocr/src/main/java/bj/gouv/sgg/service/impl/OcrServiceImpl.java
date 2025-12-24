package bj.gouv.sgg.service.impl;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.entity.ErrorCorrection;
import bj.gouv.sgg.exception.CorruptedPdfException;
import bj.gouv.sgg.repository.ErrorCorrectionRepository;
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
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.bytedeco.leptonica.global.leptonica.*;

/**
 * Implémentation améliorée du service OCR.
 * Combine les fonctionnalités de OcrServiceImpl et OcrServiceImplV2.
 */
@Slf4j
public class OcrServiceImpl implements OcrService {

    private Path tesseractDir;

    private final AppConfig config;
    private final ErrorCorrectionRepository errorCorrectionRepository;


    public OcrServiceImpl(AppConfig config, ErrorCorrectionRepository errorCorrectionRepository) {
        this.config = config;
        this.errorCorrectionRepository = errorCorrectionRepository;
    }

    @Override
    public void performOcr(File pdfFile, File ocrFile) {
        try {
            String text = extractTextFromFile(pdfFile.toPath());
            FileExistenceHelper.ensureExists(ocrFile.toPath().getParent(), "Ensure OCR output dir");
            Files.writeString(ocrFile.toPath(), text);
            log.info("✅ OCR completed: {} -> {} ({} chars)", pdfFile.getName(), ocrFile.getName(), text.length());
        } catch (CorruptedPdfException e) {
            log.error("🚨 PDF corrompu: {}", pdfFile.getName());
            throw e;
        } catch (IOException e) {
            log.error("❌ OCR failed for {}: {}", pdfFile.getName(), e.getMessage());
            throw new IllegalStateException("I/O error during OCR for " + pdfFile, e);
        }
    }

    private String extractTextFromFile(Path pdfPath) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            return extractTextFromDocument(document, pdfPath.getFileName().toString());
        } catch (IOException e) {
            if (isCorrupted(e)) {
                throw new CorruptedPdfException(pdfPath.getFileName().toString(), "PDF corrompu", e);
            }
            throw e;
        }
    }

    private String extractTextFromDocument(PDDocument document, String filename) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        String directText = stripper.getText(document);

        if (calculateTextQuality(directText) >= config.getOcrQualityThreshold() && directText.length() > 100) {
            return directText;
        }

        log.info("🔄 Qualité faible ou scan détecté pour {}. Lancement de l'OCR...", filename);
        return extractWithOcr(document);
    }

    private String extractWithOcr(PDDocument document) throws IOException {
        Path tessDir = extractTesseractData();
        TessBaseAPI api = new TessBaseAPI();

        if (api.Init(tessDir.toString(), "fra") != 0) {
            throw new IOException("Failed to initialize Tesseract (datapath=" + tessDir + ")");
        }

        api.SetPageSegMode(org.bytedeco.tesseract.global.tesseract.PSM_AUTO);
        api.SetVariable("tessedit_char_whitelist",
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.,'-()°/àâéèêëîïôûùçœŒ ");
        // Ajout de la gestion des fichiers words et patterns pour Tesseract
        String allWords = tessDir.resolve("fra.user-legal-words").toAbsolutePath() + "," +
                          tessDir.resolve("fra.user-region-words").toAbsolutePath() + "," +
                          tessDir.resolve("db-corrections.user-words").toAbsolutePath();

        String allPatterns = tessDir.resolve("fra.user-legal-pattern").toAbsolutePath() + "," +
                             tessDir.resolve("fra.user-minister-pattern").toAbsolutePath() + "," +
                             tessDir.resolve("fra.user-president-pattern").toAbsolutePath();

        api.SetVariable("user_words_file", allWords);
        api.SetVariable("user_patterns_file", allPatterns);

        PDFRenderer renderer = new PDFRenderer(document);
        StringBuilder sb = new StringBuilder();

        try {
            int pageCount = document.getNumberOfPages();
            log.info("🔄 OCR processing: {} pages", pageCount);

            for (int i = 0; i < pageCount; i++) {
                try {
                    // DPI adapatif pour Raspberry Pi
                    int dpi = config.getOcrDpi();
                    if (config.getCapacity() != null && config.getCapacity().getOcr() <= 2) {
                        dpi = Math.min(dpi, 200);
                    }

                    BufferedImage bImg = renderer.renderImageWithDPI(i, dpi);
                    PIX pixSource = convertToPix(bImg);

                    // --- AJOUT DU TRAITEMENT D'IMAGE POUR DOCUMENTS ANCIENS ---
                    // 1. Passage en niveaux de gris
                    PIX pixGray = pixConvertRGBToGray(pixSource, 0.3f, 0.5f, 0.2f);
                    // 2. Binarisation (Otsu) : Rend le fond blanc pur et le texte noir pur
                    PIX pixBinarized = pixThresholdToBinary(pixGray, 115);

                    api.SetImage(pixBinarized); // On envoie l'image nettoyée

                    BytePointer outText = api.GetUTF8Text();
                    String pageText = outText.getString(StandardCharsets.UTF_8);
                    sb.append(pageText).append("\n");

                    outText.deallocate();
                    pixDestroy(pixSource);
                    pixDestroy(pixGray);
                    pixDestroy(pixBinarized);

                    if (pageText.contains("AMPLIATIONS")) {
                        log.info("🚨 AMPLIATIONS detected at page {}/{} (stopping OCR)", i + 1, pageCount);
                        break;
                    }

                } catch (IOException e) {
                    log.warn("⚠️ Failed to OCR page {}/{}: {}", i + 1, pageCount, e.getMessage());
                }

                // Log de progression
                if ((i + 1) % 10 == 0 || i == pageCount - 1) {
                    log.info("📊 OCR progress: {}/{} pages", i + 1, pageCount);
                }
            }
        } finally {
            api.End();
            api.close();
        }
        return sb.toString();
    }

    private PIX convertToPix(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] bytes = baos.toByteArray();
        return pixReadMem(bytes, bytes.length);
    }

    private synchronized Path extractTesseractData() throws IOException {

        // cleanOldTempDirectories();

        if (tesseractDir == null) {
            // Récupération des mots de correction depuis la base de données
            Set<String> correctionDb = this.errorCorrectionRepository.findAll()
                    .stream()
                    .map(ErrorCorrection::getCorrectionText)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            log.info("Loading DB ...");

            // Création d'un répertoire temporaire qui contiendra les données Tesseract
            tesseractDir = Files.createTempDirectory("tessdata_sgg");

            // Ajout de logs pour suivre les étapes de extractTesseractData
            log.info("📂 Création du répertoire temporaire pour Tesseract: {}", tesseractDir);

            // Copie des fichiers de base présents dans les resources (tessdata)
            String[] resourceFiles = { "fra.traineddata", "fra.user-legal-words", "fra.user-legal-pattern",
                    "fra.user-minister-pattern", "fra.user-president-pattern", "fra.user-region-words" };
            log.info("📥 Copie des fichiers de base depuis les ressources...");
            for (String file : resourceFiles) {
                log.info("   - Copie du fichier: {}", file);
                try (InputStream is = OcrServiceImpl.class.getResourceAsStream("/tessdata/" + file)) {
                    if (is != null) {
                        Files.copy(is, tesseractDir.resolve(file), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            // Ajout du dictionnaire français à partir de liste.de.mots.francais.frgut.txt
            Path frenchDictionary = tesseractDir.resolve("liste.de.mots.francais.frgut.txt");
            try (InputStream dictStream = getClass().getResourceAsStream("/liste.de.mots.francais.frgut.txt")) {
                if (dictStream != null) {
                    Files.copy(dictStream, frenchDictionary, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            // Nettoyage/filtrage des fichiers words et patterns et création des fichiers DB utilisables par Tesseract
            Pattern allowed = Pattern.compile("^[\\p{L}0-9'\\-().,&%/ ]{1,120}$");

            // Liste des fichiers words à normaliser (le gros frgut sera limité)
            String[] wordsFiles = { "fra.user-legal-words", "fra.user-region-words", "liste.de.mots.francais.frgut.txt" };
            for (String wf : wordsFiles) {
                Path src = tesseractDir.resolve(wf);
                if (!Files.exists(src)) continue;

                LinkedHashSet<String> normalized = new LinkedHashSet<>();
                try (BufferedReader r = Files.newBufferedReader(src, StandardCharsets.UTF_8)) {
                    String line;
                    int limit = wf.endsWith("frgut.txt") ? 20000 : Integer.MAX_VALUE; // limiter le gros dictionnaire
                    int count = 0;
                    while ((line = r.readLine()) != null) {
                        String s = line.replace("\"", "").trim();
                        if (s.isEmpty()) continue;
                        if (s.length() > 120) continue;
                        if (!allowed.matcher(s).matches()) continue;
                        if (normalized.add(s)) count++;
                        if (count >= limit) break;
                    }
                }
                try (BufferedWriter w = Files.newBufferedWriter(src, StandardCharsets.UTF_8)) {
                    for (String s : normalized) {
                        w.write(s);
                        w.newLine();
                    }
                }
                log.info("🔧 Fichier nettoyé: {} ({} entrées)", wf, Files.lines(src).count());
            }

            // Construire le fichier final fra.user-legal-words en ajoutant les mots DB
            Path userWordsPath = tesseractDir.resolve("fra.user-legal-words");
            StringBuilder combined = new StringBuilder();
            for (String word : correctionDb) {
                String cleaned = word.replace("\"", "").trim();
                if (!cleaned.isEmpty()) combined.append(cleaned).append('\n');
            }

            String[] patternFiles = { "fra.user-legal-pattern", "fra.user-minister-pattern", "fra.user-president-pattern", "fra.user-region-words" };
            for (String patternFile : patternFiles) {
                Path p = tesseractDir.resolve(patternFile);
                if (Files.exists(p)) combined.append(Files.readString(p)).append('\n');
            }
            Files.writeString(userWordsPath, combined.toString(), java.nio.file.StandardOpenOption.APPEND);

            // Génération des fichiers db-generated.user-words et db-generated.user-patterns
            Path dbWords = tesseractDir.resolve("db-generated.user-words");
            Path dbPatterns = tesseractDir.resolve("db-generated.user-patterns");
            try (BufferedWriter wWords = Files.newBufferedWriter(dbWords);
                 BufferedWriter wPatterns = Files.newBufferedWriter(dbPatterns)) {
                for (String text : correctionDb) {
                    String cleaned = text.replace("\"", "").trim();
                    if (cleaned.isEmpty()) continue;
                    if (cleaned.contains(" ")) {
                        wPatterns.write(cleaned);
                        wPatterns.newLine();
                    } else {
                        wWords.write(cleaned);
                        wWords.newLine();
                    }
                }
            }

            // Création du fichier db-corrections.user-words (physique pour Tesseract)
            Path dbWordsPath = tesseractDir.resolve("db-corrections.user-words");
            try (BufferedWriter writer = Files.newBufferedWriter(dbWordsPath, StandardCharsets.UTF_8)) {
                for (String word : correctionDb) {
                    String cleaned = word.replace("\"", "").trim();
                    if (!cleaned.isEmpty()) {
                        writer.write(cleaned);
                        writer.newLine();
                    }
                }
            }
            log.info("📡 {} mots de correction MySQL chargés dans l'OCR.", correctionDb.size());
        }
        return tesseractDir;
    }

    private void cleanOldTempDirectories() {
        try {
            Path tempSystemDir = Path.of(System.getProperty("java.io.tmpdir"));
            Files.list(tempSystemDir)
                    .filter(path -> path.getFileName().toString().startsWith("tessdata_sgg"))
                    .forEach(path -> {
                        try {
                            deleteRecursively(path);
                            log.info("🧹 Ancien dossier temp supprimé : {}", path);
                        } catch (IOException e) {
                            log.warn("⚠️ Impossible de supprimer {}, il est peut-être utilisé.", path);
                        }
                    });
        } catch (IOException e) {
            log.error("❌ Erreur lors du nettoyage des anciens temp", e);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (Path entry : entries.collect(java.util.stream.Collectors.toList())) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    @Override
    public double calculateTextQuality(String text) {
        if (text == null || text.isBlank()) return 0.0;
        long alnum = text.chars().filter(Character::isLetterOrDigit).count();
        return (double) alnum / text.length();
    }

    private boolean isCorrupted(IOException e) {
        String m = e.getMessage();
        return m != null && (m.contains("root") || m.contains("versioninfo") || m.contains("endobj"));
    }

    @Override
    public String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return extractTextFromDocument(document, "memory_stream");
        }
    }
}
