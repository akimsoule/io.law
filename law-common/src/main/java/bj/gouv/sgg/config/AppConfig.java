package bj.gouv.sgg.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Configuration de l'application chargée depuis application.properties.
 * Remplace l'ancienne configuration Spring @ConfigurationProperties.
 * 
 * Pattern Singleton justifié : Configuration unique chargée une seule fois au démarrage.
 */
@Data
@Slf4j
@SuppressWarnings({"java:S6548", "java:S1450"}) // Singleton justifié pour config globale, champs utilisés par Lombok getters
public class AppConfig {
    
    // Champs de configuration utilisés via getters Lombok @Data
    private String baseUrl;
    private String userAgent;
    private Path storagePath;
    private Path pdfDir;
    private Path ocrDir;
    private Path jsonDir;
    private Path unrecognizedWordsFile;
    private int httpTimeout;
    private int maxRetries;
    private long retryDelay;
    private int chunkSize;
    private int maxThreads;
    private int maxDocumentsToExtract;
    private int maxItemsToFetchPrevious;
    private int jobTimeoutMinutes;
    
    // OCR properties
    private String ocrLanguage;
    private int ocrDpi;
    private double ocrQualityThreshold;
    
    // IA properties
    private CapacityConfig capacity;
    private GroqConfig groq;
    
    private static AppConfig instance;
    
    public static synchronized AppConfig load() {
        if (instance == null) {
            instance = new AppConfig();
            instance.loadFromProperties();
        }
        return instance;
    }
    
    public static AppConfig get() {
        if (instance == null) {
            return load();
        }
        return instance;
    }
    
    private void loadFromProperties() {
        Properties props = new Properties();
        
        // Charger application.properties
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            log.warn("Could not load application.properties: {}", e.getMessage());
        }
        
        // Charger database.properties
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("database.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            log.warn("Could not load database.properties: {}", e.getMessage());
        }
        
        // Law properties
        this.baseUrl = props.getProperty("law.base-url", "https://sgg.gouv.bj/doc");
        this.userAgent = props.getProperty("law.user-agent", 
            "Mozilla/5.0 (compatible; LawBatchBot/1.0)");
        
        // Storage (depuis database.properties)
        String basePath = props.getProperty("law.storage.base-path", "data");
        this.storagePath = Paths.get(basePath);
        this.pdfDir = storagePath.resolve(props.getProperty("law.storage.pdf-dir", "pdfs"));
        this.ocrDir = storagePath.resolve(props.getProperty("law.storage.ocr-dir", "ocr"));
        this.jsonDir = storagePath.resolve(props.getProperty("law.storage.json-dir", "articles"));
        this.unrecognizedWordsFile = storagePath.resolve(
            props.getProperty("law.storage.unrecognized-words-file", "word_non_recognize.txt"));
        
        // HTTP
        this.httpTimeout = Integer.parseInt(props.getProperty("law.http.timeout", "30000"));
        this.maxRetries = Integer.parseInt(props.getProperty("law.http.max-retries", "3"));
        this.retryDelay = Long.parseLong(props.getProperty("law.http.retry-delay", "2000"));
        
        // Batch
        this.chunkSize = Integer.parseInt(props.getProperty("law.batch.chunk-size", "10"));
        this.maxThreads = Integer.parseInt(props.getProperty("law.batch.max-threads", "10"));
        this.maxDocumentsToExtract = Integer.parseInt(
            props.getProperty("law.batch.max-documents-to-extract", "50"));
        this.maxItemsToFetchPrevious = Integer.parseInt(
            props.getProperty("law.batch.max-items-to-fetch-previous", "100"));
        this.jobTimeoutMinutes = Integer.parseInt(
            props.getProperty("law.batch.job-timeout-minutes", "55"));
        
        // OCR
        this.ocrLanguage = props.getProperty("law.ocr.language", "fra");
        this.ocrDpi = Integer.parseInt(props.getProperty("law.ocr.dpi", "300"));
        this.ocrQualityThreshold = Double.parseDouble(
            props.getProperty("law.ocr.quality-threshold", "0.5"));
        
        // IA - Capacity
        this.capacity = new CapacityConfig();
        this.capacity.ia = Integer.parseInt(props.getProperty("law.capacity.ia", "0"));
        this.capacity.ocr = Integer.parseInt(props.getProperty("law.capacity.ocr", "2"));
        this.capacity.ollamaUrl = props.getProperty("law.capacity.ollama-url", "http://localhost:11434");
        this.capacity.ollamaModelsRequired = props.getProperty("law.capacity.ollama-models-required", "qwen2.5:7b");
        
        // IA - Groq
        this.groq = new GroqConfig();
        this.groq.apiKey = props.getProperty("law.groq.api-key", "");
        this.groq.baseUrl = props.getProperty("law.groq.base-url", "https://api.groq.com/openai/v1");
        
        log.info("📋 Configuration loaded: baseUrl={}, storagePath={}", baseUrl, storagePath);
    }
    
    /**
     * Récupère une propriété avec valeur par défaut.
     */
    public String getProperty(String key, String defaultValue) {
        if ("law.storage.base-path".equals(key)) return storagePath.toString();
        if ("law.storage.pdf-dir".equals(key)) return pdfDir.getFileName().toString();
        if ("law.storage.ocr-dir".equals(key)) return ocrDir.getFileName().toString();
        if ("law.storage.json-dir".equals(key)) return jsonDir.getFileName().toString();
        return defaultValue;
    }
    
    public Path getDbPath() {
        return storagePath.resolve("db");
    }
    
    public Path getDocumentsPath() {
        return getDbPath().resolve("documents.json");
    }
    
    public Path getNotFoundPath() {
        return getDbPath().resolve("not_found.json");
    }
    
    public Path getFetchedPath() {
        return getDbPath().resolve("fetched.json");
    }
    
    public Path getDownloadedPath() {
        return getDbPath().resolve("downloaded.json");
    }
    
    public Path getExtractedPath() {
        return getDbPath().resolve("extracted.json");
    }
    
    public Path getConsolidatedPath() {
        return getDbPath().resolve("consolidated.json");
    }
    
    public Path getFetchResultsPath() {
        return getDbPath().resolve("fetch_results.json");
    }
    
    public Path getDownloadResultsPath() {
        return getDbPath().resolve("download_results.json");
    }
    
    public Path getConsolidationResultsPath() {
        return getDbPath().resolve("consolidation_results.json");
    }
    
    public Path getFetchCursorsPath() {
        return getDbPath().resolve("fetch_cursors.json");
    }
    
    /**
     * Configuration des capacités IA/OCR
     */
    @Data
    @SuppressWarnings("java:S1450") // Champs utilisés par Lombok getters
    public static class CapacityConfig {
        private int ia;
        private int ocr;
        private String ollamaUrl;
        private String ollamaModelsRequired;
    }
    
    /**
     * Configuration Groq API
     */
    @Data
    @SuppressWarnings("java:S1450") // Champs utilisés par Lombok getters
    public static class GroqConfig {
        private String apiKey;
        private String baseUrl;
    }
}
