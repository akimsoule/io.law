package bj.gouv.sgg.service.impl;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.config.ArticleExtractorConfig;
import bj.gouv.sgg.model.Article;
import bj.gouv.sgg.model.DocumentMetadata;
import bj.gouv.sgg.model.OcrExtractionResult;
import bj.gouv.sgg.service.ArticleExtractionService;
import bj.gouv.sgg.service.extract.impl.OcrExtractionServiceImpl;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implémentation du service d'extraction d'articles avec pattern Reader-Processor-Writer.
 * 
 * Architecture:
 * - READER: Récupère les fichiers OCR .txt à traiter
 * - PROCESSOR: Extrait articles et métadonnées via OcrExtractionServiceImpl
 * - WRITER: Sauvegarde les résultats en JSON
 */
@Slf4j
public class ArticleExtractionServiceImpl implements ArticleExtractionService {
    
    private static ArticleExtractionServiceImpl instance;
    
    private final AppConfig config;
    private final OcrExtractionServiceImpl extractionService;
    private final Gson gson;
    
    private final List<ExtractionResult> extractionResults;
    private int successCount;
    private int failedCount;
    
    private ArticleExtractionServiceImpl() {
        this.config = AppConfig.get();
        ArticleExtractorConfig extractorConfig = new ArticleExtractorConfig();
        extractorConfig.initialize();
        this.extractionService = new OcrExtractionServiceImpl(extractorConfig);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.extractionResults = new ArrayList<>();
    }
    
    public static synchronized ArticleExtractionServiceImpl getInstance() {
        if (instance == null) {
            instance = new ArticleExtractionServiceImpl();
        }
        return instance;
    }
    
    @Override
    public void runType(String type) {
        log.info("🔄 ArticleExtractionService: type={}", type);
        
        // Réinitialiser compteurs
        this.successCount = 0;
        this.failedCount = 0;
        this.extractionResults.clear();
        
        // ========== READER: Récupérer fichiers OCR à traiter ==========
        List<File> ocrFiles = readOcrFiles(type);
        
        if (ocrFiles.isEmpty()) {
            log.warn("⚠️ Aucun fichier OCR à traiter");
            return;
        }
        
        // ========== PROCESSOR: Extraire articles ==========
        log.info("📥 Processing {} OCR files...", ocrFiles.size());
        for (File ocrFile : ocrFiles) {
            processOcrFile(ocrFile, type);
        }
        
        // ========== WRITER: Sauvegarder résultats ==========
        writeExtractionResults(this.extractionResults);
        
        // ========== STATISTIQUES ==========
        log.info("✅ ArticleExtractionService terminé: {} succès, {} échecs", successCount, failedCount);
    }
    
    @Override
    public void runDocument(String documentId) {
        log.info("🔄 Article extraction: documentId={}", documentId);
        
        // Réinitialiser compteurs
        this.successCount = 0;
        this.failedCount = 0;
        this.extractionResults.clear();
        
        try {
            String[] parts = documentId.split("-");
            if (parts.length != 3) {
                log.warn("⚠️ Format invalide: {}", documentId);
                return;
            }
            
            String type = parts[0];
            Path ocrPath = getOcrPath(type, documentId);
            
            if (!ocrPath.toFile().exists()) {
                log.warn("⚠️ OCR non trouvé: {}", documentId);
                return;
            }
            
            Path jsonPath = getJsonPath(type, documentId);
            if (jsonPath.toFile().exists()) {
                log.debug("⏭️ JSON existe déjà: {}", documentId);
                return;
            }
            
            // PROCESSOR
            processOcrFile(ocrPath.toFile(), type);
            
            // WRITER
            writeExtractionResults(this.extractionResults);
            
            log.info("✅ ArticleExtractionService terminé: {} succès, {} échecs", successCount, failedCount);
            
        } catch (Exception e) {
            log.error("❌ Erreur extraction articles pour {}: {}", documentId, e.getMessage());
        }
    }
    
    // ========== READER ==========
    
    /**
     * READER: Récupère les fichiers OCR à traiter.
     * - Charge fichiers .txt depuis ocr/{type}/
     * - Filtre ceux dont le JSON n'existe pas
     * - Trie par année et numéro (décroissant)
     * - Limite selon config.maxDocumentsToExtract
     * 
     * @return Liste des fichiers OCR à traiter
     */
    private List<File> readOcrFiles(String type) {
        log.info("📖 READER: Récupération fichiers OCR pour type '{}'...", type);
        
        Path ocrDir = config.getStoragePath().resolve("ocr").resolve(type);
        if (!ocrDir.toFile().exists()) {
            log.warn("⚠️ Répertoire OCR non trouvé: {}", ocrDir);
            return List.of();
        }
        
        File[] files = ocrDir.toFile().listFiles((dir, name) -> {
            if (!name.endsWith(".txt")) {
                return false;
            }
            // Vérifier si le JSON n'existe pas déjà
            String documentId = name.substring(0, name.length() - 4);
            Path jsonPath = getJsonPath(type, documentId);
            return !jsonPath.toFile().exists();
        });
        
        if (files == null || files.length == 0) {
            log.warn("⚠️ Aucun fichier OCR à traiter dans: {}", ocrDir);
            return List.of();
        }
        
        // Trier par année et numéro (décroissant)
        sortByYearAndNumber(files);
        
        // Limiter selon configuration
        int maxToProcess = config.getMaxDocumentsToExtract();
        List<File> result = new ArrayList<>(Arrays.asList(files));
        
        if (maxToProcess > 0 && result.size() > maxToProcess) {
            result = result.subList(0, maxToProcess);
            log.info("📖 READER: Limitation à {} fichiers (sur {} disponibles)", maxToProcess, files.length);
        }
        
        log.info("📖 READER: {} fichiers OCR à traiter", result.size());
        return result;
    }
    
    // ========== PROCESSOR ==========
    
    /**
     * PROCESSOR: Extrait articles d'un fichier OCR.
     * - Lit le fichier OCR
     * - Extrait articles via OcrExtractionServiceImpl
     * - Extrait métadonnées
     * - Calcule confiance
     * - Crée ExtractionResult
     * - Ajoute à la liste des résultats
     */
    private void processOcrFile(File ocrFile, String type) {
        String documentId = ocrFile.getName().replace(".txt", "");
        log.debug("⚙️ PROCESSOR: {}", documentId);
        
        try {
            String ocrText = Files.readString(ocrFile.toPath());
            if (ocrText.trim().isEmpty()) {
                log.warn("⚠️ OCR vide: {}", documentId);
                failedCount++;
                return;
            }
            
            // Extraction
            List<Article> articles = extractionService.extractArticles(ocrText);
            DocumentMetadata metadata = extractionService.extractMetadata(ocrText);
            double confidence = extractionService.calculateConfidence(ocrText, articles, documentId);
            
            OcrExtractionResult extraction = OcrExtractionResult.builder()
                .articles(articles)
                .metadata(metadata)
                .confidence(confidence)
                .method("OCR")
                .timestamp(java.time.LocalDateTime.now().toString())
                .build();
            
            Path jsonPath = getJsonPath(type, documentId);
            this.extractionResults.add(new ExtractionResult(jsonPath, extraction, documentId, articles.size()));
            
            log.info("✅ Extrait: {} → {} articles (confiance: {:.2f})", documentId, articles.size(), confidence);
            successCount++;
            
        } catch (Exception e) {
            log.error("❌ Erreur extraction {}: {}", documentId, e.getMessage());
            failedCount++;
        }
    }
    
    // ========== WRITER ==========
    
    /**
     * WRITER: Sauvegarde tous les résultats en JSON.
     * Crée les répertoires nécessaires et écrit les fichiers JSON.
     */
    private void writeExtractionResults(List<ExtractionResult> results) {
        if (results.isEmpty()) {
            log.info("💾 WRITER: Aucun résultat à sauvegarder");
            return;
        }
        
        log.info("💾 WRITER: Sauvegarde de {} résultats...", results.size());
        
        for (ExtractionResult result : results) {
            try {
                Files.createDirectories(result.jsonPath.getParent());
                Files.writeString(result.jsonPath, gson.toJson(result.extraction));
                log.debug("💾 Sauvegardé: {}", result.documentId);
            } catch (IOException e) {
                log.error("❌ Erreur sauvegarde {}: {}", result.documentId, e.getMessage());
            }
        }
        
        log.info("💾 WRITER: ✅ Sauvegarde terminée");
    }
    
    // ========== HELPERS ==========
    
    private void sortByYearAndNumber(File[] files) {
        Arrays.sort(files, (f1, f2) -> {
            try {
                String name1 = f1.getName().replace(".txt", "");
                String name2 = f2.getName().replace(".txt", "");
                String[] parts1 = name1.split("-");
                String[] parts2 = name2.split("-");
                
                if (parts1.length >= 3 && parts2.length >= 3) {
                    int year1 = Integer.parseInt(parts1[1]);
                    int year2 = Integer.parseInt(parts2[1]);
                    if (year1 != year2) return Integer.compare(year2, year1);
                    
                    int number1 = Integer.parseInt(parts1[2]);
                    int number2 = Integer.parseInt(parts2[2]);
                    return Integer.compare(number2, number1);
                }
            } catch (Exception e) {
                log.debug("Unable to parse year/number from files");
            }
            return 0;
        });
    }
    
    private Path getOcrPath(String type, String documentId) {
        return config.getStoragePath().resolve("ocr").resolve(type).resolve(documentId + ".txt");
    }
    
    private Path getJsonPath(String type, String documentId) {
        return config.getStoragePath().resolve("articles").resolve(type).resolve(documentId + ".json");
    }
    
    // ========== INNER CLASS ==========
    
    /**
     * Résultat d'extraction à sauvegarder.
     */
    private static class ExtractionResult {
        final Path jsonPath;
        final OcrExtractionResult extraction;
        final String documentId;
        final int articleCount;
        
        ExtractionResult(Path jsonPath, OcrExtractionResult extraction, String documentId, int articleCount) {
            this.jsonPath = jsonPath;
            this.extraction = extraction;
            this.documentId = documentId;
            this.articleCount = articleCount;
        }
    }
}
