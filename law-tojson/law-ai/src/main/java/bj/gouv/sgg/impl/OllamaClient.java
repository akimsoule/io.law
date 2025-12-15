package bj.gouv.sgg.impl;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.service.IAService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Implémentation IAService pour Ollama local
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaClient implements IAService {

    private static final String OLLAMA_GENERATE_ENDPOINT = "/api/generate";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int TIMEOUT_SECONDS = 300;  // 5 minutes pour génération IA

    private final LawProperties properties;
    private final Gson gson = new Gson();
    private OkHttpClient client;

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
    public String getSourceName() {
        return "IA:OLLAMA";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // 1. Vérifier si Ollama répond (ping)
            String url = properties.getCapacity().getOllamaUrl() + "/api/tags";
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();
            
            try (Response response = getClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.debug("Ollama ping failed: HTTP {}", response.code());
                    return false;
                }
                
                // 2. Vérifier si le modèle requis est disponible
                String responseBody = response.body() != null ? response.body().string() : "{}";
                JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                
                if (!json.has("models")) {
                    log.debug("Ollama response missing 'models' field");
                    return false;
                }
                
                String requiredModel = properties.getCapacity().getOllamaModelsRequired();
                log.debug("Checking for required model: {}", requiredModel);
                
                // Vérifier si le modèle existe (version flexible : "gemma3n" ou "gemma3n:latest")
                String modelPrefix = requiredModel.split(":")[0];
                boolean modelFound = json.getAsJsonArray("models").asList().stream()
                        .anyMatch(model -> {
                            String modelName = model.getAsJsonObject().get("name").getAsString();
                            return modelName.startsWith(modelPrefix);
                        });
                
                if (!modelFound) {
                    log.debug("Required model '{}' not found in Ollama", requiredModel);
                    return false;
                }
                
                log.debug("✅ Ollama available with model: {}", requiredModel);
                return true;
            }
        } catch (Exception e) {
            log.debug("Ollama availability check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String correctOcrText(String rawOcrText, String prompt) throws IAException {
        try {
            log.debug("🤖 Correction OCR via Ollama ({} caractères)", rawOcrText.length());
            
            // Utiliser la méthode complete() pour envoyer le prompt de correction
            String correctedText = complete(prompt);
            
            log.debug("✅ OCR corrigé via Ollama ({} caractères)", correctedText.length());
            return correctedText;
            
        } catch (Exception e) {
            log.error("❌ Échec correction OCR Ollama: {}", e.getMessage());
            throw new IAException("Ollama OCR correction failed", e);
        }
    }
    
    /**
     * Envoie un prompt textuel simple à Ollama (sans image).
     * 
     * <p>Utilisé par AIOrchestrator pour correction OCR sans traitement d'image.
     * 
     * @param prompt Prompt complet à envoyer
     * @return Réponse JSON de l'IA (texte brut)
     * @throws IAException Si erreur réseau ou parsing
     */
    public String complete(String prompt) throws IAException {
        try {
            String ollamaUrl = properties.getCapacity().getOllamaUrl();
            String model = properties.getCapacity().getOllamaModelsRequired();
            
            // Construire requête JSON
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("prompt", prompt);
            requestBody.addProperty("stream", false);
            
            JsonObject options = new JsonObject();
            options.addProperty("temperature", 0.1);
            options.addProperty("num_predict", 4000);
            requestBody.add("options", options);
            
            // Envoyer requête via OkHttp
            RequestBody body = RequestBody.create(requestBody.toString(), JSON_MEDIA_TYPE);
            Request request = new Request.Builder()
                    .url(ollamaUrl + OLLAMA_GENERATE_ENDPOINT)
                    .post(body)
                    .build();
            
            try (Response response = getClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IAException("Ollama HTTP " + response.code() + ": " + response.message());
                }
                
                String responseBodyStr = response.body().string();
                JsonObject jsonResponse = com.google.gson.JsonParser.parseString(responseBodyStr).getAsJsonObject();
                
                if (!jsonResponse.has("response")) {
                    throw new IAException("Ollama response missing 'response' field");
                }
                
                return jsonResponse.get("response").getAsString();
            }
            
        } catch (IOException e) {
            throw new IAException("Ollama complete failed: " + e.getMessage(), e);
        }
    }

}
