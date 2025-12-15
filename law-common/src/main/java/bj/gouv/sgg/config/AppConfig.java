package bj.gouv.sgg.config;

import lombok.Data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Configuration de l'application chargée depuis application.properties.
 * Remplace l'ancienne configuration Spring @ConfigurationProperties.
 */
@Data
public class AppConfig {
    
    private String baseUrl;
    private String userAgent;
    private Path storagePath;
    private Path pdfDir;
    private Path ocrDir;
    private Path jsonDir;
    private int httpTimeout;
    private int maxRetries;
    private long retryDelay;
    private int chunkSize;
    private int maxThreads;
    private int maxDocumentsToExtract;
    private int maxItemsToFetchPrevious;
    private int jobTimeoutMinutes;
    
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
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load application.properties: " + e.getMessage());
        }
        
        // Law properties
        this.baseUrl = props.getProperty("law.base-url", "https://sgg.gouv.bj/doc");
        this.userAgent = props.getProperty("law.user-agent", 
            "Mozilla/5.0 (compatible; LawBatchBot/1.0)");
        
        // Storage
        String basePath = props.getProperty("law.storage.base-path", "data");
        this.storagePath = Paths.get(basePath);
        this.pdfDir = storagePath.resolve(props.getProperty("law.storage.pdf-dir", "pdfs"));
        this.ocrDir = storagePath.resolve(props.getProperty("law.storage.ocr-dir", "ocr"));
        this.jsonDir = storagePath.resolve(props.getProperty("law.storage.json-dir", "articles"));
        
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
    
    public Path getFetchResultsPath() {
        return getDbPath().resolve("fetch_results.json");
    }
    
    public Path getFetchCursorsPath() {
        return getDbPath().resolve("fetch_cursors.json");
    }
    
    public Path getDownloadResultsPath() {
        return getDbPath().resolve("download_results.json");
    }
    
    public Path getDocumentsPath() {
        return getDbPath().resolve("documents.json");
    }
}
