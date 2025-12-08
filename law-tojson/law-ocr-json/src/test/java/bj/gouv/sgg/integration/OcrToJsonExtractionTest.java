package bj.gouv.sgg.integration;

import bj.gouv.sgg.config.ArticleExtractorConfig;
import bj.gouv.sgg.impl.ArticleRegexExtractor;
import bj.gouv.sgg.impl.CsvCorrector;
import bj.gouv.sgg.model.Article;
import bj.gouv.sgg.model.DocumentMetadata;
import bj.gouv.sgg.service.OcrExtractionService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test d'extraction et génération de fichiers JSON depuis les échantillons OCR
 * 
 * Ce test lit les fichiers samples_ocr/, effectue l'extraction complète,
 * et génère les fichiers JSON dans samples_json/
 */
@Slf4j
class OcrToJsonExtractionTest {
    
    private ArticleExtractorConfig config;
    private CsvCorrector corrector;
    private OcrExtractionService extractionService;
    private Gson gson;
    
    private Path samplesOcrPath;
    private Path samplesJsonPath;
    
    @BeforeEach
    void setUp() throws IOException {
        // Initialisation composants
        config = new ArticleExtractorConfig();
        config.init();
        
        corrector = new CsvCorrector();
        extractionService = new ArticleRegexExtractor(config);
        
        gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
        
        // Chemins
        samplesOcrPath = Paths.get("src/test/resources/samples_ocr");
        samplesJsonPath = Paths.get("src/test/resources/samples_json");
        
        // Créer dossier samples_json s'il n'existe pas
        Files.createDirectories(samplesJsonPath);
        Files.createDirectories(samplesJsonPath.resolve("loi"));
        Files.createDirectories(samplesJsonPath.resolve("decret"));
    }
    
    @Test
    void givenAllOcrSamplesWhenExtractAndGenerateJsonThenProcessesAllFiles() throws IOException {
        int totalProcessed = 0;
        int successfulExtractions = 0;
        int totalArticles = 0;
        
        // Traiter tous les fichiers loi/
        log.info("========== Extraction LOIS ==========");
        try (Stream<Path> paths = Files.walk(samplesOcrPath.resolve("loi"))) {
            List<Path> loiFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".txt"))
                .sorted()
                .toList();
            
            for (Path ocrFile : loiFiles) {
                totalProcessed++;
                try {
                    boolean success = processAndGenerateJson(ocrFile, "loi");
                    if (success) {
                        successfulExtractions++;
                        totalArticles += getArticleCount(ocrFile);
                    }
                } catch (Exception e) {
                    log.warn("❌ Échec {} : {}", ocrFile.getFileName(), e.getMessage());
                }
            }
        }
        
        // Traiter tous les fichiers decret/
        log.info("========== Extraction DECRETS ==========");
        try (Stream<Path> paths = Files.walk(samplesOcrPath.resolve("decret"))) {
            List<Path> decretFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".txt"))
                .sorted()
                .toList();
            
            for (Path ocrFile : decretFiles) {
                totalProcessed++;
                try {
                    boolean success = processAndGenerateJson(ocrFile, "decret");
                    if (success) {
                        successfulExtractions++;
                        totalArticles += getArticleCount(ocrFile);
                    }
                } catch (Exception e) {
                    log.warn("❌ Échec {} : {}", ocrFile.getFileName(), e.getMessage());
                }
            }
        }
        
        // Rapport final
        log.info("=".repeat(50));
        log.info("📊 RAPPORT FINAL");
        log.info("=".repeat(50));
        log.info("✅ Fichiers traités : {}/{}", successfulExtractions, totalProcessed);
        log.info("📄 Total articles extraits : {}", totalArticles);
        log.info("📈 Taux de succès : {}%", 
                 totalProcessed > 0 ? (successfulExtractions * 100 / totalProcessed) : 0);
        log.info("=".repeat(50));
        
        // Assertions
        assertTrue(totalProcessed > 0, "Au moins 1 fichier devrait être traité");
        assertTrue(successfulExtractions >= totalProcessed / 2, 
                   "Au moins 50% des fichiers devraient être extraits");
    }
    
    private boolean processAndGenerateJson(Path ocrFile, String type) throws IOException {
        String fileName = ocrFile.getFileName().toString();
        String baseName = fileName.replace(".txt", "");
        
        log.info("🔄 Traitement : {}", fileName);
        
        // Étape 1 : Lire OCR
        String rawOcr = Files.readString(ocrFile);
        
        // Étape 2 : Correction
        String corrected = corrector.applyCorrections(rawOcr);
        
        // Étape 3 : Extraction articles
        List<Article> articles = extractionService.extractArticles(corrected);
        
        if (articles.isEmpty()) {
            log.warn("⚠️ {} : Aucun article extrait", fileName);
            return false;
        }
        
        // Étape 4 : Extraction métadonnées
        DocumentMetadata metadata = extractionService.extractMetadata(corrected);
        
        // Étape 5 : Calcul confiance
        double confidence = extractionService.calculateConfidence(corrected, articles);
        
        // Étape 6 : Construire JSON
        JsonObject jsonOutput = buildJsonOutput(baseName, type, articles, metadata, confidence);
        
        // Étape 7 : Sauvegarder JSON
        Path jsonFile = samplesJsonPath.resolve(type).resolve(baseName + ".json");
        Files.writeString(jsonFile, gson.toJson(jsonOutput));
        
        log.info("✅ {} : {} articles, confiance {}, JSON généré", 
                 fileName, articles.size(), String.format("%.2f", confidence));
        
        return true;
    }
    
    private JsonObject buildJsonOutput(String documentId, String type, 
                                      List<Article> articles, 
                                      DocumentMetadata metadata, 
                                      double confidence) {
        JsonObject json = new JsonObject();
        
        // Parse document ID (ex: loi-2024-1)
        String[] parts = documentId.split("-");
        if (parts.length >= 3) {
            json.addProperty("type", parts[0]);
            json.addProperty("year", Integer.parseInt(parts[1]));
            json.addProperty("number", Integer.parseInt(parts[2]));
        } else {
            json.addProperty("type", type);
            json.addProperty("documentId", documentId);
        }
        
        // Métadonnées
        JsonObject metadataJson = new JsonObject();
        metadataJson.addProperty("confidence", confidence);
        metadataJson.addProperty("method", "OCR");
        metadataJson.addProperty("timestamp", java.time.LocalDateTime.now().toString());
        json.add("_metadata", metadataJson);
        
        // Titre
        if (metadata.getLawTitle() != null) {
            json.addProperty("title", metadata.getLawTitle());
        }
        
        // Date promulgation
        if (metadata.getPromulgationDate() != null) {
            json.addProperty("promulgationDate", metadata.getPromulgationDate());
        }
        
        // Ville promulgation
        if (metadata.getPromulgationCity() != null) {
            json.addProperty("promulgationCity", metadata.getPromulgationCity());
        }
        
        // Articles
        com.google.gson.JsonArray articlesArray = new com.google.gson.JsonArray();
        for (Article article : articles) {
            JsonObject articleJson = new JsonObject();
            articleJson.addProperty("number", String.valueOf(article.getIndex()));
            articleJson.addProperty("content", article.getContent());
            articlesArray.add(articleJson);
        }
        json.add("articles", articlesArray);
        
        // Signataires
        if (metadata.getSignatories() != null && !metadata.getSignatories().isEmpty()) {
            com.google.gson.JsonArray signatariesArray = new com.google.gson.JsonArray();
            int order = 1;
            for (var signatory : metadata.getSignatories()) {
                JsonObject signatoryJson = new JsonObject();
                signatoryJson.addProperty("name", signatory.getName());
                signatoryJson.addProperty("title", signatory.getRole());
                signatoryJson.addProperty("order", order++);
                signatariesArray.add(signatoryJson);
            }
            json.add("signatories", signatariesArray);
        }
        
        return json;
    }
    
    private int getArticleCount(Path ocrFile) {
        try {
            String rawOcr = Files.readString(ocrFile);
            String corrected = corrector.applyCorrections(rawOcr);
            List<Article> articles = extractionService.extractArticles(corrected);
            return articles.size();
        } catch (Exception e) {
            return 0;
        }
    }
    
    @Test
    void givenGeneratedJsonFilesWhenVerifyJsonFilesThenConfirmsFilesExist() throws IOException {
        // Exécuter d'abord l'extraction
        givenAllOcrSamplesWhenExtractAndGenerateJsonThenProcessesAllFiles();
        
        // Vérifier que des fichiers JSON ont été créés
        long jsonLoiCount = 0;
        long jsonDecretCount = 0;
        
        if (Files.exists(samplesJsonPath.resolve("loi"))) {
            try (Stream<Path> paths = Files.walk(samplesJsonPath.resolve("loi"))) {
                jsonLoiCount = paths.filter(p -> p.toString().endsWith(".json")).count();
            }
        }
        
        if (Files.exists(samplesJsonPath.resolve("decret"))) {
            try (Stream<Path> paths = Files.walk(samplesJsonPath.resolve("decret"))) {
                jsonDecretCount = paths.filter(p -> p.toString().endsWith(".json")).count();
            }
        }
        
        log.info("📊 Fichiers JSON générés : {} lois, {} décrets", jsonLoiCount, jsonDecretCount);
        
        assertTrue(jsonLoiCount > 0 || jsonDecretCount > 0, 
                   "Au moins 1 fichier JSON devrait être généré");
    }
    
    @Test
    void givenGeneratedJsonFilesWhenValidateJsonQualityThenAssessesMetadataAndConfidence() throws IOException {
        int totalJsonFiles = 0;
        int validJsonFiles = 0;
        int filesWithMetadata = 0;
        int filesWithHighConfidence = 0;
        
        // Valider tous les JSON loi/
        if (Files.exists(samplesJsonPath.resolve("loi"))) {
            try (Stream<Path> paths = Files.walk(samplesJsonPath.resolve("loi"))) {
                List<Path> jsonFiles = paths
                    .filter(p -> p.toString().endsWith(".json"))
                    .toList();
                
                for (Path jsonFile : jsonFiles) {
                    totalJsonFiles++;
                    
                    String content = Files.readString(jsonFile);
                    JsonObject json = gson.fromJson(content, JsonObject.class);
                    
                    // Vérifier structure de base
                    if (validateJsonStructure(json)) {
                        validJsonFiles++;
                    }
                    
                    // Vérifier métadonnées
                    if (json.has("title") || json.has("promulgationDate") || json.has("promulgationCity")) {
                        filesWithMetadata++;
                    }
                    
                    // Vérifier confiance élevée
                    if (json.has("_metadata")) {
                        JsonObject metadata = json.getAsJsonObject("_metadata");
                        if (metadata.has("confidence")) {
                            double confidence = metadata.get("confidence").getAsDouble();
                            if (confidence >= 0.7) {
                                filesWithHighConfidence++;
                            }
                        }
                    }
                }
            }
        }
        
        // Valider tous les JSON decret/
        if (Files.exists(samplesJsonPath.resolve("decret"))) {
            try (Stream<Path> paths = Files.walk(samplesJsonPath.resolve("decret"))) {
                List<Path> jsonFiles = paths
                    .filter(p -> p.toString().endsWith(".json"))
                    .toList();
                
                for (Path jsonFile : jsonFiles) {
                    totalJsonFiles++;
                    
                    String content = Files.readString(jsonFile);
                    JsonObject json = gson.fromJson(content, JsonObject.class);
                    
                    if (validateJsonStructure(json)) {
                        validJsonFiles++;
                    }
                    
                    if (json.has("title") || json.has("promulgationDate") || json.has("promulgationCity")) {
                        filesWithMetadata++;
                    }
                    
                    if (json.has("_metadata")) {
                        JsonObject metadata = json.getAsJsonObject("_metadata");
                        if (metadata.has("confidence")) {
                            double confidence = metadata.get("confidence").getAsDouble();
                            if (confidence >= 0.7) {
                                filesWithHighConfidence++;
                            }
                        }
                    }
                }
            }
        }
        
        log.info("=".repeat(50));
        log.info("📊 VALIDATION QUALITÉ JSON");
        log.info("=".repeat(50));
        log.info("✅ Fichiers valides : {}/{}", validJsonFiles, totalJsonFiles);
        log.info("📝 Avec métadonnées : {}/{}", filesWithMetadata, totalJsonFiles);
        log.info("🎯 Confiance ≥0.7 : {}/{}", filesWithHighConfidence, totalJsonFiles);
        log.info("=".repeat(50));
        
        assertTrue(totalJsonFiles > 0, "Au moins 1 fichier JSON devrait exister");
        assertEquals(totalJsonFiles, validJsonFiles, "Tous les JSON devraient être valides");
        assertTrue(filesWithHighConfidence >= totalJsonFiles * 0.3, 
                   "Au moins 30% des fichiers devraient avoir une confiance ≥0.7");
    }
    
    private boolean validateJsonStructure(JsonObject json) {
        // Vérifier champs obligatoires
        if (!json.has("type") || !json.has("year") || !json.has("number")) {
            return false;
        }
        
        // Vérifier métadonnées
        if (!json.has("_metadata")) {
            return false;
        }
        
        JsonObject metadata = json.getAsJsonObject("_metadata");
        if (!metadata.has("confidence") || !metadata.has("method") || !metadata.has("timestamp")) {
            return false;
        }
        
        // Vérifier articles
        if (!json.has("articles")) {
            return false;
        }
        
        com.google.gson.JsonArray articles = json.getAsJsonArray("articles");
        if (articles.size() == 0) {
            return false;
        }
        
        // Vérifier structure articles
        for (int i = 0; i < articles.size(); i++) {
            JsonObject article = articles.get(i).getAsJsonObject();
            if (!article.has("number") || !article.has("content")) {
                return false;
            }
        }
        
        return true;
    }
}
