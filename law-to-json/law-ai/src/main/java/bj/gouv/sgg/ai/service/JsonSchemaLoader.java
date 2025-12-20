package bj.gouv.sgg.ai.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Service pour charger les schémas JSON et templates de prompts depuis les resources.
 */
@Slf4j
@Service
public class JsonSchemaLoader {

    private static final String LAW_DOCUMENT_SCHEMA_PATH = "format/law-document-schema.json";
    private static final String OCR_TO_JSON_PROMPT_PATH = "prompts/ocr-to-json.txt";
    
    private final Gson gson = new Gson();
    
    private JsonObject lawDocumentSchema;
    private String ocrToJsonPrompt;

    @PostConstruct
    public void init() {
        try {
            loadSchemas();
            log.info("✅ JSON schemas loaded successfully");
        } catch (IOException e) {
            log.error("❌ Failed to load JSON schemas", e);
            throw new RuntimeException("Cannot start without JSON schemas", e);
        }
    }

    private void loadSchemas() throws IOException {
        // Charger le schéma JSON principal
        ClassPathResource schemaResource = new ClassPathResource(LAW_DOCUMENT_SCHEMA_PATH);
        String schemaContent = new String(schemaResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        lawDocumentSchema = gson.fromJson(schemaContent, JsonObject.class);
        
        // Charger le prompt OCR → JSON
        ClassPathResource promptResource = new ClassPathResource(OCR_TO_JSON_PROMPT_PATH);
        ocrToJsonPrompt = new String(promptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        
        log.debug("Loaded law document schema: {} properties", 
                lawDocumentSchema.getAsJsonObject("schema")
                        .getAsJsonObject("properties")
                        .size());
    }

    /**
     * Obtient le schéma JSON complet pour les documents juridiques.
     * Utilisé par Groq (OpenAI Structured Outputs).
     */
    public JsonObject getLawDocumentSchema() {
        return lawDocumentSchema.deepCopy(); // Retourner une copie pour éviter les modifications
    }

    /**
     * Obtient le prompt OCR → JSON.
     * Contient déjà les instructions complètes et le schéma.
     */
    public String getOcrToJsonPrompt() {
        return ocrToJsonPrompt;
    }

    /**
     * Construit le prompt complet pour Ollama en ajoutant le texte OCR.
     * 
     * @param ocrText Le texte OCR à analyser
     * @return Le prompt formaté avec le texte OCR
     */
    public String enhancePromptForOllama(String ocrText) {
        return String.format(ocrToJsonPrompt, ocrText);
    }

    /**
     * Construit l'objet response_format pour Groq.
     */
    public JsonObject buildGroqResponseFormat() {
        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_schema");
        responseFormat.add("json_schema", getLawDocumentSchema());
        return responseFormat;
    }
}
