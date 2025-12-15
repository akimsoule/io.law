package bj.gouv.sgg.ai.provider.impl;

import bj.gouv.sgg.ai.model.AIRequest;
import bj.gouv.sgg.ai.model.AIResponse;
import bj.gouv.sgg.ai.provider.IAProvider;
import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.exception.IAException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Implémentation IAProvider pour Ollama local.
 * 
 * <p><b>Avantages</b> :
 * <ul>
 *   <li>Gratuit et local (pas de coûts API)</li>
 *   <li>Confidentialité données</li>
 *   <li>Pas de limite de requêtes</li>
 * </ul>
 * 
 * <p><b>Limites</b> :
 * <ul>
 *   <li>Nécessite ressources locales (RAM, GPU)</li>
 *   <li>Plus lent que cloud</li>
 *   <li>Moins de modèles disponibles</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OllamaProvider implements IAProvider {

    private static final String OLLAMA_GENERATE_ENDPOINT = "/api/generate";
    private static final String OLLAMA_TAGS_ENDPOINT = "/api/tags";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    
    private final LawProperties properties;
    private final Gson gson;
    private OkHttpClient client;

    private OkHttpClient getClient() {
        if (client == null) {
            int timeout = 300; // 5 minutes
            client = new OkHttpClient.Builder()
                    .connectTimeout(timeout, TimeUnit.SECONDS)
                    .readTimeout(timeout, TimeUnit.SECONDS)
                    .writeTimeout(timeout, TimeUnit.SECONDS)
                    .build();
        }
        return client;
    }

    @Override
    public String getProviderName() {
        return "OLLAMA";
    }

    @Override
    public boolean isAvailable() {
        try {
            String url = properties.getCapacity().getOllamaUrl() + OLLAMA_TAGS_ENDPOINT;
            Request request = new Request.Builder().url(url).get().build();
            
            try (Response response = getClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return false;
                }
                
                String responseBody = response.body() != null ? response.body().string() : "{}";
                JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                
                if (!json.has("models") || json.getAsJsonArray("models").isEmpty()) {
                    return false;
                }
                
                log.debug("✅ Ollama available with {} models", 
                         json.getAsJsonArray("models").size());
                return true;
            }
        } catch (Exception e) {
            log.debug("Ollama not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public AIResponse complete(AIRequest request) throws IAException {
        long startTime = System.currentTimeMillis();
        
        try {
            String ollamaUrl = properties.getCapacity().getOllamaUrl();
            
            // Construire requête JSON
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", request.getModel());
            requestBody.addProperty("prompt", request.getPrompt());
            requestBody.addProperty("stream", request.isStream());
            
            // Options
            JsonObject options = new JsonObject();
            options.addProperty("temperature", request.getTemperature());
            options.addProperty("num_predict", request.getMaxTokens());
            requestBody.add("options", options);
            
            // Images si présentes (vision)
            if (!request.getImagesBase64().isEmpty()) {
                com.google.gson.JsonArray images = new com.google.gson.JsonArray();
                request.getImagesBase64().forEach(images::add);
                requestBody.add("images", images);
            }
            
            // Envoyer requête
            RequestBody body = RequestBody.create(requestBody.toString(), JSON_MEDIA_TYPE);
            Request httpRequest = new Request.Builder()
                    .url(ollamaUrl + OLLAMA_GENERATE_ENDPOINT)
                    .post(body)
                    .build();
            
            try (Response response = getClient().newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new IAException("Ollama request failed: HTTP " + response.code());
                }
                
                String responseBody = response.body() != null ? response.body().string() : "{}";
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                
                if (!jsonResponse.has("response")) {
                    throw new IAException("Ollama response missing 'response' field");
                }
                
                String generatedText = jsonResponse.get("response").getAsString();
                long durationMs = System.currentTimeMillis() - startTime;
                
                // Extraire métadonnées
                int tokensUsed = 0;
                if (jsonResponse.has("eval_count")) {
                    tokensUsed = jsonResponse.get("eval_count").getAsInt();
                }
                
                return AIResponse.builder()
                        .generatedText(generatedText)
                        .model(request.getModel())
                        .provider(getProviderName())
                        .tokensUsed(tokensUsed)
                        .processingTimeMs(durationMs)
                        .build();
            }
            
        } catch (IOException e) {
            throw new IAException("Ollama request failed", e);
        }
    }

    @Override
    public ProviderCapabilities getCapabilities() {
        // Ollama supporte vision avec certains modèles (llava, bakllava, etc.)
        return new ProviderCapabilities(
                true,  // supportsVision
                true,  // supportsStreaming
                8192,  // maxContextTokens (varie selon modèle)
                10,    // maxImageSizeMB
                5      // maxImagesPerRequest
        );
    }

    @Override
    public List<ModelInfo> getAvailableModels() {
        List<ModelInfo> models = new ArrayList<>();
        String requiredModel = properties.getCapacity().getOllamaModelsRequired();
        
        try {
            String url = properties.getCapacity().getOllamaUrl() + OLLAMA_TAGS_ENDPOINT;
            Request request = new Request.Builder().url(url).get().build();
            
            try (Response response = getClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return models;
                }
                
                String responseBody = response.body() != null ? response.body().string() : "{}";
                JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                
                if (json.has("models")) {
                    json.getAsJsonArray("models").forEach(element -> {
                        JsonObject model = element.getAsJsonObject();
                        String name = model.get("name").getAsString();
                        
                        // Filtrer par le modèle requis configuré
                        if (!name.equals(requiredModel)) {
                            return; // Skip ce modèle
                        }
                        
                        // Détecter support vision par nom
                        boolean supportsVision = name.contains("llava") || 
                                                name.contains("bakllava") ||
                                                name.contains("vision");
                        
                        models.add(new ModelInfo(
                                name,
                                supportsVision,
                                8192, // Context par défaut
                                model.has("description") ? 
                                    model.get("description").getAsString() : "Ollama model"
                        ));
                    });
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Ollama models: {}", e.getMessage());
        }
        
        return models;
    }

    @Override
    public Optional<ModelInfo> selectBestModel(boolean requiresVision, int estimatedTokens) {
        List<ModelInfo> availableModels = getAvailableModels();
        
        if (availableModels.isEmpty()) {
            return Optional.empty();
        }
        
        // Filtrer par capacités
        return availableModels.stream()
                .filter(m -> !requiresVision || m.supportsVision())
                .filter(m -> m.contextWindow() >= estimatedTokens)
                .findFirst();
    }
}
