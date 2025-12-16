package bj.gouv.sgg.service.impl;

import bj.gouv.sgg.ai.service.AIOrchestrator;
import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.provider.IAProvider;
import bj.gouv.sgg.provider.IAProviderFactory;
import bj.gouv.sgg.provider.impl.GroqProvider;
import bj.gouv.sgg.provider.impl.NoIAProvider;
import bj.gouv.sgg.provider.impl.OllamaProvider;
import bj.gouv.sgg.service.IAService;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;

/**
 * Implémentation du service d'extraction IA.
 * 
 * <p>Délègue l'orchestration à {@link AIOrchestrator} qui gère :
 * <ul>
 *   <li>Sélection du meilleur provider IA (Ollama/Groq)</li>
 *   <li>Gestion des transformations (OCR correction, JSON extraction)</li>
 *   <li>Gestion des fallbacks si transformation échoue</li>
 * </ul>
 * 
 * @author io.law
 * @since 1.0.0
 */
@Slf4j
public class IAServiceImpl implements IAService {
    
    private static IAServiceImpl instance;
    
    private final IAProviderFactory providerFactory;
    private final AIOrchestrator orchestrator;
    
    private IAServiceImpl() {
        // Charger la configuration
        bj.gouv.sgg.config.AppConfig config = bj.gouv.sgg.config.AppConfig.get();
        
        // Créer OkHttpClient et Gson
        okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        com.google.gson.Gson gson = new com.google.gson.Gson();
        
        // Initialiser les providers avec les dépendances requises
        OllamaProvider ollamaProvider = new OllamaProvider(config, httpClient, gson);
        GroqProvider groqProvider = new GroqProvider(config, gson);
        NoIAProvider noIAProvider = new NoIAProvider();
        
        this.providerFactory = new IAProviderFactory(ollamaProvider, groqProvider, noIAProvider);
        this.orchestrator = new AIOrchestrator(providerFactory, Collections.emptyList());
    }
    
    public static synchronized IAServiceImpl getInstance() {
        if (instance == null) {
            instance = new IAServiceImpl();
        }
        return instance;
    }
    
    public IAServiceImpl(IAProviderFactory providerFactory, AIOrchestrator orchestrator) {
        this.providerFactory = providerFactory;
        this.orchestrator = orchestrator;
    }
    
    @Override
    public String correctOcrText(String rawOcrText, String prompt) throws IAException {
        if (rawOcrText == null || rawOcrText.isEmpty()) {
            throw new IAException("Raw OCR text cannot be null or empty");
        }
        
        if (prompt == null || prompt.isEmpty()) {
            throw new IAException("Prompt cannot be null or empty");
        }
        
        try {
            log.debug("🔧 Correcting OCR text ({} chars) with IA", rawOcrText.length());
            
            // Créer un LawDocument minimal pour l'orchestrator
            // Dans une utilisation réelle (via IAExtractionJob), un vrai document sera passé
            bj.gouv.sgg.model.LawDocument minimalDoc = new bj.gouv.sgg.model.LawDocument();
            minimalDoc.setType("loi");
            minimalDoc.setYear(java.time.Year.now().getValue());
            minimalDoc.setNumber(0);
            
            // Utiliser l'orchestrator pour corriger le texte OCR
            String correctedText = orchestrator.correctOcr(minimalDoc, rawOcrText);
            
            log.info("✅ OCR text corrected via {}", getSourceName());
            return correctedText;
            
        } catch (Exception e) {
            log.error("❌ IA correction failed: {}", e.getMessage());
            throw new IAException("Failed to correct OCR text: " + e.getMessage(), e);
        }
    }
    
    @Override
    public JsonObject extractJsonFromOcr(String ocrText) throws IAException {
        if (ocrText == null || ocrText.isEmpty()) {
            throw new IAException("OCR text cannot be null or empty");
        }
        
        try {
            log.debug("📄 Extracting JSON from OCR text ({} chars)", ocrText.length());
            
            // Créer un LawDocument minimal pour l'orchestrator
            bj.gouv.sgg.model.LawDocument minimalDoc = new bj.gouv.sgg.model.LawDocument();
            minimalDoc.setType("loi");
            minimalDoc.setYear(java.time.Year.now().getValue());
            minimalDoc.setNumber(0);
            
            // Utiliser l'orchestrator pour extraire le JSON
            JsonObject extractedJson = orchestrator.ocrToJson(minimalDoc, ocrText);
            
            log.info("✅ JSON extracted via {}", getSourceName());
            return extractedJson;
            
        } catch (Exception e) {
            log.error("❌ JSON extraction failed: {}", e.getMessage());
            throw new IAException("Failed to extract JSON: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getSourceName() {
        try {
            // Récupérer le provider actif depuis la factory
            IAProvider activeProvider = providerFactory.selectProvider(false, 1000);
            return activeProvider.getProviderName();
        } catch (Exception e) {
            log.warn("⚠️ Could not determine active provider: {}", e.getMessage());
            return "UNKNOWN";
        }
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Vérifier si au moins un provider IA est disponible (pas NoIA)
            return providerFactory.hasAnyProvider();
        } catch (Exception e) {
            log.warn("⚠️ Could not check IA availability: {}", e.getMessage());
            return false;
        }
    }
}
