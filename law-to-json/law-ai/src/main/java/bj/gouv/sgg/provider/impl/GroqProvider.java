package bj.gouv.sgg.provider.impl;

import bj.gouv.sgg.ai.model.AIRequest;
import bj.gouv.sgg.ai.model.AIResponse;
import bj.gouv.sgg.provider.IAProvider;
import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.exception.IAException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Implémentation IAProvider pour Groq Cloud API.
 * 
 * <p><b>Avantages</b> :
 * <ul>
 *   <li>Très rapide (latence réduite)</li>
 *   <li>Pas de ressources locales nécessaires</li>
 *   <li>Modèles puissants (Llama 3, Mixtral, etc.)</li>
 * </ul>
 * 
 * <p><b>Limites</b> :
 * <ul>
 *   <li>Nécessite API key et connexion internet</li>
 *   <li>Coûts potentiels</li>
 *   <li>Limites de quotas</li>
 * </ul>
 */
@Slf4j
public class GroqProvider implements IAProvider {

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int TIMEOUT_SECONDS = 120; // 2 minutes
    private static final String CONTENT_FIELD = "content";
    
    private final AppConfig properties;
    private final Gson gson;
    private final bj.gouv.sgg.ai.service.JsonSchemaLoader jsonSchemaLoader;
    private OkHttpClient client;
    
    public GroqProvider(AppConfig properties, Gson gson, 
                       bj.gouv.sgg.ai.service.JsonSchemaLoader jsonSchemaLoader) {
        this.properties = properties;
        this.gson = gson;
        this.jsonSchemaLoader = jsonSchemaLoader;
    }

    private OkHttpClient getClient() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build();
        }
        return client;
    }

    @Override
    public String getProviderName() {
        return "GROQ";
    }

    @Override
    public boolean isAvailable() {
        String apiKey = properties.getGroq().getApiKey();
        
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("Groq API key not configured");
            return false;
        }
        
        // Tenter une requête simple pour vérifier l'API
        try {
            AIRequest testRequest = AIRequest.builder()
                    .model("llama-3.3-70b-versatile")
                    .prompt("test")
                    .maxTokens(10)
                    .build();
            
            complete(testRequest);
            log.debug("✅ Groq API available");
            return true;
            
        } catch (Exception e) {
            log.debug("Groq not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public AIResponse complete(AIRequest request) throws IAException {
        String apiKey = properties.getGroq().getApiKey();
        
        if (apiKey == null || apiKey.isBlank()) {
            throw new IAException("Groq API key not configured");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Construire requête OpenAI-compatible
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", request.getModel());
            requestBody.addProperty("temperature", request.getTemperature());
            requestBody.addProperty("max_tokens", request.getMaxTokens());
            requestBody.addProperty("stream", request.isStream());
            
            // Utiliser le schéma JSON depuis resources
            JsonObject responseFormat = jsonSchemaLoader.buildGroqResponseFormat();
            requestBody.add("response_format", responseFormat);
            
            // Messages format
            com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            
            // Si images présentes, format vision
            if (!request.getImagesBase64().isEmpty()) {
                com.google.gson.JsonArray content = new com.google.gson.JsonArray();
                
                // Texte
                JsonObject textPart = new JsonObject();
                textPart.addProperty("type", "text");
                textPart.addProperty("text", request.getPrompt());
                content.add(textPart);
                
                // Images
                for (String imageBase64 : request.getImagesBase64()) {
                    JsonObject imagePart = new JsonObject();
                    imagePart.addProperty("type", "image_url");
                    
                    JsonObject imageUrl = new JsonObject();
                    imageUrl.addProperty("url", "data:image/jpeg;base64," + imageBase64);
                    imagePart.add("image_url", imageUrl);
                    
                    content.add(imagePart);
                }
                
                message.add(CONTENT_FIELD, content);
            } else {
                // Texte simple
                message.addProperty(CONTENT_FIELD, request.getPrompt());
            }
            
            messages.add(message);
            requestBody.add("messages", messages);
            
            // Envoyer requête
            RequestBody body = RequestBody.create(requestBody.toString(), JSON_MEDIA_TYPE);
            Request httpRequest = new Request.Builder()
                    .url(GROQ_API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();
            
            try (Response response = getClient().newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    throw new IAException("Groq API failed: HTTP " + response.code() + " - " + errorBody);
                }
                
                String responseBody = response.body() != null ? response.body().string() : "{}";
                
                // Valider que la réponse est du JSON valide
                JsonObject jsonResponse;
                try {
                    jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                } catch (com.google.gson.JsonSyntaxException e) {
                    throw new IAException("Groq returned invalid JSON: " + e.getMessage());
                }
                
                if (!jsonResponse.has("choices")) {
                    throw new IAException("Groq response missing 'choices' field");
                }
                
                JsonObject choice = jsonResponse.getAsJsonArray("choices").get(0).getAsJsonObject();
                JsonObject messageResponse = choice.getAsJsonObject("message");
                String generatedText = messageResponse.get(CONTENT_FIELD).getAsString();
                
                long durationMs = System.currentTimeMillis() - startTime;
                
                // Extraire usage
                int tokensUsed = 0;
                if (jsonResponse.has("usage")) {
                    JsonObject usage = jsonResponse.getAsJsonObject("usage");
                    tokensUsed = usage.get("total_tokens").getAsInt();
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
            throw new IAException("Groq request failed", e);
        }
    }

    @Override
    public ProviderCapabilities getCapabilities() {
        return new ProviderCapabilities(
                true,  // supportsVision (certains modèles)
                true,  // supportsStreaming
                32768, // maxContextTokens (llama-3.3-70b)
                20,    // maxImageSizeMB
                10     // maxImagesPerRequest (estimation)
        );
    }

    @Override
    public List<ModelInfo> getAvailableModels() {
        List<ModelInfo> models = new ArrayList<>();
        
        // Modèles Groq connus (au 11 décembre 2025)
        models.add(new ModelInfo("llama-3.3-70b-versatile", false, 32768, "Llama 3.3 70B Versatile"));
        models.add(new ModelInfo("llama-3.1-70b-versatile", false, 32768, "Llama 3.1 70B Versatile"));
        models.add(new ModelInfo("mixtral-8x7b-32768", false, 32768, "Mixtral 8x7B"));
        models.add(new ModelInfo("llama-3.2-90b-vision-preview", true, 8192, "Llama 3.2 90B Vision"));
        
        return models;
    }

    @Override
    public Optional<ModelInfo> selectBestModel(boolean requiresVision, int estimatedTokens) {
        List<ModelInfo> availableModels = getAvailableModels();
        
        return availableModels.stream()
                .filter(m -> !requiresVision || m.supportsVision())
                .filter(m -> m.contextWindow() >= estimatedTokens)
                .findFirst();
    }
}
