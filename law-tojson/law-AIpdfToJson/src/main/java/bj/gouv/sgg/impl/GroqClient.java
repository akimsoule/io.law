package bj.gouv.sgg.impl;

import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.exception.PromptLoadException;
import bj.gouv.sgg.modele.JsonResult;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.service.IAService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final String ARTICLES = "articles";
    private static final String DEFAULT_MODEL = "llama-3.1-8b-instant";
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
    public JsonResult transform(LawDocument document, Path pdfPath) throws IAException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IAException("Groq API key not configured");
        }

        if (!Files.exists(pdfPath)) {
            log.warn("PDF file not found: {}", pdfPath);
            return new JsonResult(
                    "{\"documentId\":\"" + document.getDocumentId() + "\",\"articles\":[]}",
                    0.2,
                    "IA:GROQ:FILE_NOT_FOUND"
            );
        }

        try {
            // Charger le prompt adapté
            String promptFilename = "decret".equals(document.getType())
                    ? "decret-parser.txt"
                    : "pdf-parser.txt";
            String promptTemplate = loadPrompt(promptFilename);

            if (promptTemplate.isBlank()) {
                throw new IAException("Cannot load " + promptFilename + " prompt");
            }

            // Charger le contenu OCR ou PDF
            String textContent = document.getOcrContent() != null
                    ? document.getOcrContent()
                    : "PDF content";

            // Formatter le prompt
            String prompt = promptTemplate.replace("{text}", textContent);

            // Appeler Groq
            String responseText = generateText(prompt, null);
            String json = cleanJsonResponse(responseText);
            double confidence = estimateConfidenceFromValidation(json, textContent.length());

            log.info("✅ Groq transformation completed for {}: confidence={}",
                    document.getDocumentId(), confidence);
            return new JsonResult(json, confidence, "IA:GROQ");

        } catch (Exception e) {
            log.error("❌ Groq transformation failed for {}: {}", document.getDocumentId(), e.getMessage());
            return new JsonResult(
                    "{\"documentId\":\"" + document.getDocumentId() + "\",\"error\":\"" + e.getMessage() + "\"}",
                    0.1,
                    "IA:GROQ:ERROR"
            );
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

    private String generateText(String prompt, String systemPrompt) throws IAException {
        try {
            JsonObject requestBody = buildRequestBody(prompt, systemPrompt);

            Request request = new Request.Builder()
                    .url(GROQ_API_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(gson.toJson(requestBody), JSON_MEDIA_TYPE))
                    .build();

            log.debug("🔄 Sending request to Groq API...");

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

    private JsonObject buildRequestBody(String prompt, String systemPrompt) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model != null ? model : DEFAULT_MODEL);
        requestBody.addProperty("temperature", 0.1);
        requestBody.addProperty("max_tokens", 4000);

        com.google.gson.JsonArray messages = new com.google.gson.JsonArray();

        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty(CONTENT_KEY, systemPrompt);
            messages.add(systemMessage);
        }

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty(CONTENT_KEY, prompt);
        messages.add(userMessage);

        requestBody.add("messages", messages);

        return requestBody;
    }

    private String cleanJsonResponse(String response) {
        if (response == null || response.isBlank()) {
            return "{\"articles\":[]}";
        }

        int startIdx = response.indexOf('{');
        int endIdx = response.lastIndexOf('}');

        if (startIdx >= 0 && endIdx > startIdx) {
            return response.substring(startIdx, endIdx + 1);
        }
        return "{\"articles\":[]}";
    }

    private double estimateConfidenceFromValidation(String json, int sourceTextLength) {
        double baseScore = 0.5;

        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            if (obj.has("documentId")) baseScore += 0.15;
            if (obj.has("type")) baseScore += 0.1;
            if (obj.has("title")) baseScore += 0.1;

            if (obj.has(ARTICLES) && obj.get(ARTICLES).isJsonArray()) {
                int articleCount = obj.getAsJsonArray(ARTICLES).size();
                baseScore += 0.2;
                if (articleCount > 0) baseScore += 0.15;
                if (articleCount >= 3) baseScore += 0.1;
            }

            if (json.length() < 100) {
                baseScore = Math.max(0.15, baseScore - 0.3);
            }

            if (sourceTextLength > 2000) {
                baseScore = Math.min(0.95, baseScore + 0.1);
            }

        } catch (Exception e) {
            log.debug("JSON parsing error: {}", e.getMessage());
            baseScore = 0.2;
        }

        return Math.max(0.15, Math.min(0.95, baseScore));
    }
}
