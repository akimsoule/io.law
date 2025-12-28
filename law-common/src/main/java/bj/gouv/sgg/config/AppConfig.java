package bj.gouv.sgg.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration de l'application avec Spring Boot @ConfigurationProperties.
 * Migration Spring Batch - Remplace le pattern Singleton par injection Spring.
 */
@Component
@ConfigurationProperties(prefix = "law")
@Data
@Slf4j
public class AppConfig {

    // Storage
    private String storageBasePath = "data";

    // HTTP
    private String baseUrl = "https://sgg.gouv.bj/doc";
    private String userAgent = "Mozilla/5.0 (compatible; LawBatchBot/1.0)";
    private int httpTimeout = 30000;
    private int maxRetries = 3;
    private long retryDelay = 2000;

    // Batch
    private int chunkSize = 10;
    private int maxThreads = 10;
    private int maxItemsToExtract = 50;
    private int maxItemsToFetchPrevious = 100;
    private int jobTimeoutMinutes = 55;

    // OCR
    private String ocrLanguage = "fra";
    private int ocrDpi = 300;
    private double ocrQualityThreshold = 0.5;

    // IA
    private CapacityConfig capacity = new CapacityConfig();
    private GroqConfig groq = new GroqConfig();

    // Chemins calculés
    private transient Path storagePath;
    private transient Path pdfDir;
    private transient Path imagesDir;
    private transient Path ocrDir;
    private transient Path jsonDir;
    private transient Path unrecognizedWordsFile;

    @PostConstruct
    public void init() {
        // Vérifier que la propriété a bien été fournie (fallback safety)
        if (storageBasePath == null || storageBasePath.isBlank()) {
            throw new IllegalStateException("Property 'law.storage-base-path' must be set in application.yml");
        }

        // Initialiser les chemins
        this.storagePath = Paths.get(storageBasePath);
        this.pdfDir = storagePath.resolve("pdfs");
        this.imagesDir = storagePath.resolve("images");
        this.ocrDir = storagePath.resolve("ocr");
        this.jsonDir = storagePath.resolve("articles");
        this.unrecognizedWordsFile = storagePath.resolve("word_non_recognize.txt");
        log.info("📋 Configuration loaded: baseUrl={}, dataAbsolutePath={}, storagePath={}, imagesDir={}",
                baseUrl, Paths.get(storageBasePath).toFile().getAbsolutePath(),
                storagePath, imagesDir);
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
