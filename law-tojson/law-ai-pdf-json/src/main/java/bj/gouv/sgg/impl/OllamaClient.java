package bj.gouv.sgg.impl;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.exception.PromptLoadException;
import bj.gouv.sgg.modele.JsonResult;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.service.IAService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
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
    private static final int TIMEOUT_SECONDS = 120;

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
                
                // Vérifier si le modèle existe (version flexible : "qwen2.5:7b" ou "qwen2.5")
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
    public JsonResult transform(LawDocument document, Path pdfPath) throws IAException {
        try {
            JsonResult result = commonTransform(document, pdfPath);
            log.info("✅ Ollama transformation completed for {}: confidence={}",
                    document.getDocumentId(), result.getConfidence());
            return result;
        } catch (Exception e) {
            log.error("❌ Ollama transformation failed for {}: {}", document.getDocumentId(), e.getMessage());
            throw new IAException("Ollama transformation failed", e);
        }
    }

    @Override
    public String loadPrompt(String filename) throws PromptLoadException {
        try (var is = getClass().getResourceAsStream("/prompts/" + filename)) {
            if (is == null) {
                throw new PromptLoadException("Prompt file not found: " + filename);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PromptLoadException("Failed to load prompt file: " + filename, e);
        }
    }

    @Override
    public String generateTextWithImages(String prompt, String systemPrompt, List<String> imagesBase64) throws IAException {
        try {
            JsonObject requestBody = buildRequestBodyWithImages(prompt, systemPrompt, imagesBase64);

            String url = properties.getCapacity().getOllamaUrl() + OLLAMA_GENERATE_ENDPOINT;
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(gson.toJson(requestBody), JSON_MEDIA_TYPE))
                    .build();

            log.debug("🔄 Sending request to Ollama @ {} with {} images...", url, imagesBase64.size());

            try (Response response = getClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    throw new IAException("Ollama API error: " + response.code() + " - " + errorBody);
                }

                String responseBody = response.body().string();

                // Ollama retourne des JSON line-delimited
                StringBuilder fullResponse = new StringBuilder();
                for (String line : responseBody.split("\n")) {
                    if (!line.trim().isEmpty()) {
                        JsonObject jsonLine = gson.fromJson(line, JsonObject.class);
                        if (jsonLine.has("response")) {
                            fullResponse.append(jsonLine.get("response").getAsString());
                        }
                    }
                }

                return fullResponse.toString();
            }
        } catch (IOException e) {
            throw new IAException("Ollama communication failed: " + e.getMessage(), e);
        }
    }

    private JsonObject buildRequestBodyWithImages(String prompt, String systemPrompt, List<String> imagesBase64) {
        JsonObject requestBody = new JsonObject();
        // Utiliser modèle vision Ollama (llava, bakllava, etc.)
        String visionModel = properties.getCapacity().getOllamaModelsRequired(); // ou bakllava, llava-llama3, etc.
        requestBody.addProperty("model", visionModel);
        requestBody.addProperty("stream", false);

        String fullPrompt = prompt;
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            fullPrompt = systemPrompt + "\n\n" + prompt;
        }

        requestBody.addProperty("prompt", fullPrompt);
        
        // Ajouter images base64 (format Ollama)
        com.google.gson.JsonArray imagesArray = new com.google.gson.JsonArray();
        for (String imageBase64 : imagesBase64) {
            imagesArray.add(imageBase64);
        }
        requestBody.add("images", imagesArray);

        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.1);
        options.addProperty("num_predict", 4000);
        requestBody.add("options", options);

        return requestBody;
    }

}
