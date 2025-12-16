package bj.gouv.sgg.job;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.config.ArticleExtractorConfig;
import bj.gouv.sgg.model.Article;
import bj.gouv.sgg.model.OcrExtractionResult;
import bj.gouv.sgg.model.DocumentMetadata;
import bj.gouv.sgg.service.JobService;
import bj.gouv.sgg.service.extract.impl.OcrExtractionServiceImpl;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Job d'extraction d'articles depuis OCR conforme à l'architecture law-consolidate.
 * 
 * <p>Workflow :
 * <ol>
 *   <li>Scanne répertoire ocr/{type}/</li>
 *   <li>Pour chaque fichier .txt OCR</li>
 *   <li>Extrait articles + métadonnées via OcrExtractionServiceImpl</li>
 *   <li>Calcule score de confiance</li>
 *   <li>Sauvegarde JSON dans articles/{type}/</li>
 * </ol>
 * 
 * <p>Ce job délègue toute la logique au {@link OcrExtractionServiceImpl}.
 * 
 * <p>Non-bloquant : Continue sur erreur avec log.
 * 
 * @see OcrExtractionServiceImpl
 * @author io.law
 * @since 1.0.0
 */
@Slf4j
public class ArticleExtractionJob implements JobService {
    
    private final OcrExtractionServiceImpl extractionService;
    private final AppConfig config;
    private final Gson gson;
    
    public ArticleExtractionJob() {
        this.config = AppConfig.get();
        ArticleExtractorConfig extractorConfig = new ArticleExtractorConfig();
        extractorConfig.initialize();
        this.extractionService = new OcrExtractionServiceImpl(extractorConfig);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    @Override
    public synchronized void runDocument(String documentId) {
        log.info("🔄 Article extraction: documentId={}", documentId);
        
        try {
            String[] parts = documentId.split("-");
            if (parts.length != 3) {
                log.warn("⚠️ Format invalide: {}", documentId);
                return;
            }
            
            String type = parts[0];
            Path ocrPath = getOcrPath(type, documentId);
            Path jsonPath = getJsonPath(type, documentId);
            
            if (!ocrPath.toFile().exists()) {
                log.warn("⚠️ OCR non trouvé: {}", documentId);
                return;
            }
            
            if (jsonPath.toFile().exists()) {
                log.debug("⏭️ JSON existe déjà: {}", documentId);
                return;
            }
            
            processOcrFile(ocrPath.toFile(), jsonPath.toFile(), documentId);
            
        } catch (Exception e) {
            log.error("❌ Erreur extraction articles pour {}: {}", documentId, e.getMessage());
        }
    }
    
    @Override
    public void runType(String type) {
        log.info("🔄 Article extraction: type={}", type);
        if (config.getMaxDocumentsToExtract() > 0) {
            log.info("📊 Limite documents: {}", config.getMaxDocumentsToExtract());
        }
        
        try {
            Path ocrDir = config.getStoragePath().resolve("ocr").resolve(type);
            if (!ocrDir.toFile().exists()) {
                log.warn("⚠️ Répertoire OCR non trouvé: {}", ocrDir);
                return;
            }
            
            File[] ocrFiles = ocrDir.toFile().listFiles((dir, name) -> {
                if (!name.endsWith(".txt")) {
                    return false;
                }
                // Vérifier si le JSON n'existe pas déjà
                String documentId = name.substring(0, name.length() - 4);
                Path jsonPath = config.getStoragePath().resolve("articles").resolve(type).resolve(documentId + ".json");
                return !jsonPath.toFile().exists();
            });

            if (ocrFiles == null || ocrFiles.length == 0) {
                log.warn("⚠️ Aucun OCR à traiter dans: {}", ocrDir);
                return;
            }
            
            sortByYearAndNumber(ocrFiles);
            log.info("📂 Found {} OCR files in {}", ocrFiles.length, type);
            
            processFiles(ocrFiles, type);
            
        } catch (Exception e) {
            log.error("❌ Article extraction failed for type {}: {}", type, e.getMessage(), e);
        }
    }
    
    private void processFiles(File[] ocrFiles, String type) {
        int maxToProcess = config.getMaxDocumentsToExtract();
        if (maxToProcess > 0 && ocrFiles.length > maxToProcess) {
            log.info("🔢 Limitation à {} documents (sur {} disponibles)", maxToProcess, ocrFiles.length);
        }
        
        int processed = 0;
        int skipped = 0;
        int failed = 0;
        
        for (File ocrFile : ocrFiles) {
            if (maxToProcess > 0 && processed >= maxToProcess) {
                log.info("🛑 Limite de {} documents atteinte", maxToProcess);
                break;
            }
            
            String documentId = ocrFile.getName().replace(".txt", "");
            Path jsonPath = getJsonPath(type, documentId);
            
            if (jsonPath.toFile().exists()) {
                skipped++;
                continue;
            }
            
            try {
                processOcrFile(ocrFile, jsonPath.toFile(), documentId);
                processed++;
            } catch (Exception e) {
                log.error("❌ Failed to extract {}: {}", documentId, e.getMessage());
                failed++;
            }
        }
        
        log.info("✅ Extraction terminée: {} traités, {} ignorés, {} échoués", processed, skipped, failed);
    }
    
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
    
    private void processOcrFile(File ocrFile, File jsonFile, String documentId) throws IOException {
        String ocrText = Files.readString(ocrFile.toPath());
        if (ocrText.trim().isEmpty()) {
            log.warn("⚠️ OCR vide: {}", documentId);
            return;
        }
        
        try {
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
            
            Files.createDirectories(jsonFile.toPath().getParent());
            Files.writeString(jsonFile.toPath(), gson.toJson(extraction));
            
            log.info("✅ {} → {} articles (confiance: {:.2f})", documentId, articles.size(), confidence);
            
        } catch (Exception e) {
            log.error("❌ Échec extraction articles pour {}: {}", documentId, e.getMessage());
            throw e;
        }
    }
}
