package bj.gouv.sgg.impl;

import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.exception.PromptLoadException;
import bj.gouv.sgg.modele.JsonResult;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.service.IAService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Implémentation IAService pour Groq Cloud API
 */
@Slf4j
@Component
public class GroqClient implements IAService {

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int TIMEOUT_SECONDS = 60;
    private static final String CONTENT_KEY = "content";

    @Value("${law.groq.api-key:}")
    private String apiKey;

    @Value("${law.groq.model:llama-3.1-8b-instant}")
    private String model;

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
        return "IA:GROQ";
    }
    
    @Override
    public boolean isAvailable() {
        // 1. Vérifier si API key configurée
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.debug("Groq API key not configured");
            return false;
        }
        
        // 2. Vérifier si l'API Groq répond (simple ping)
        try {
            String url = "https://api.groq.com/openai/v1/models";
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();
            
            try (Response response = getClient().newCall(request).execute()) {
                boolean available = response.isSuccessful();
                if (!available) {
                    log.debug("Groq API ping failed: HTTP {}", response.code());
                } else {
                    log.debug("✅ Groq API available");
                }
                return available;
            }
        } catch (Exception e) {
            log.debug("Groq availability check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public JsonResult transform(LawDocument document, Path pdfPath) throws IAException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IAException("Groq API key not configured");
        }

        try {
            JsonResult result = commonTransform(document, pdfPath);
            log.info("✅ Groq transformation completed for {}: confidence={}",
                    document.getDocumentId(), result.getConfidence());
            return result;
        } catch (Exception e) {
            log.error("❌ Groq transformation failed for {}: {}", document.getDocumentId(), e.getMessage());
            throw new IAException("Groq transformation failed", e);
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

            Request request = new Request.Builder()
                    .url(GROQ_API_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(gson.toJson(requestBody), JSON_MEDIA_TYPE))
                    .build();

            log.debug("🔄 Sending request to Groq API with {} images...", imagesBase64.size());

            try (Response response = getClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    throw new IAException("Groq API error: " + response.code() + " - " + errorBody);
                }

                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

                return jsonResponse
                        .getAsJsonArray("choices")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonObject("message")
                        .get(CONTENT_KEY)
                        .getAsString();
            }
        } catch (IOException e) {
            throw new IAException("Groq communication failed: " + e.getMessage(), e);
        }
    }

    private JsonObject buildRequestBodyWithImages(String prompt, String systemPrompt, List<String> imagesBase64) {
        JsonObject requestBody = new JsonObject();
        // Utiliser modèle vision si disponible (llama-3.2-90b-vision-preview supporte images)
        String visionModel = "llama-3.2-90b-vision-preview";
        requestBody.addProperty("model", visionModel);
        requestBody.addProperty("temperature", 0.1);
        requestBody.addProperty("max_tokens", 4000);

        com.google.gson.JsonArray messages = new com.google.gson.JsonArray();

        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty(CONTENT_KEY, systemPrompt);
            messages.add(systemMessage);
        }

        // Message utilisateur avec texte + images (format OpenAI Vision API)
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        
        com.google.gson.JsonArray contentArray = new com.google.gson.JsonArray();
        
        // Texte du prompt
        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", prompt);
        contentArray.add(textContent);
        
        // Images base64
        for (String imageBase64 : imagesBase64) {
            JsonObject imageContent = new JsonObject();
            imageContent.addProperty("type", "image_url");
            
            JsonObject imageUrl = new JsonObject();
            imageUrl.addProperty("url", "data:image/png;base64," + imageBase64);
            imageContent.add("image_url", imageUrl);
            
            contentArray.add(imageContent);
        }
        
        userMessage.add(CONTENT_KEY, contentArray);
        messages.add(userMessage);

        requestBody.add("messages", messages);

        return requestBody;
    }

}
