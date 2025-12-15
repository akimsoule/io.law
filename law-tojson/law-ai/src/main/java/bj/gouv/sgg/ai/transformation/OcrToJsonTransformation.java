package bj.gouv.sgg.ai.transformation;

import bj.gouv.sgg.ai.chunking.TextChunker;
import bj.gouv.sgg.ai.model.AIRequest;
import bj.gouv.sgg.ai.model.AIResponse;
import bj.gouv.sgg.ai.model.TransformationContext;
import bj.gouv.sgg.ai.model.TransformationResult;
import bj.gouv.sgg.ai.provider.IAProvider;
import bj.gouv.sgg.ai.service.PromptLoader;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transformation OCR → JSON structuré
 * <p>
 * Extrait la structure légale complète depuis le texte OCR :
 * - Articles avec numéros et contenu
 * - Métadonnées (titre, date, référence JO)
 * - Préambule/dispositif
 * - Signataires
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OcrToJsonTransformation implements IATransformation<String, JsonObject> {

    private final TextChunker textChunker;
    private final PromptLoader promptLoader;
    private final Gson gson;

    private static final String TRANSFORMATION_NAME = "OCR_TO_JSON";
    private static final String INDEX_FIELD = "index";
    private static final String ARTICLES_FIELD = "articles";
    private static final String PROMPT_NAME = "ocr-to-json";

    @Override
    public String getName() {
        return TRANSFORMATION_NAME;
    }

    @Override
    public Class<String> getInputType() {
        return String.class;
    }

    @Override
    public Class<JsonObject> getOutputType() {
        return JsonObject.class;
    }

    @Override
    public TransformationResult<JsonObject> transform(String ocrText, TransformationContext context) {
        long startTime = System.currentTimeMillis();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> metadata = new HashMap<>();

        try {
            log.debug("🔄 [{}] Début transformation OCR → JSON", context.getDocument().getDocumentId());

            // Validation entrée
            if (ocrText == null || ocrText.trim().isEmpty()) {
                warnings.add("Texte OCR vide ou null");
                return TransformationResult.<JsonObject>builder()
                        .output(new JsonObject())
                        .confidence(0.0)
                        .method(TRANSFORMATION_NAME)
                        .timestamp(Instant.now())
                        .durationMs(System.currentTimeMillis() - startTime)
                        .warnings(warnings)
                        .metadata(metadata)
                        .build();
            }

            // Estimation chunks
            int estimatedChunks = estimateChunks(ocrText, context);
            metadata.put("estimatedChunks", estimatedChunks);

            JsonObject result;
            if (estimatedChunks > 1) {
                log.info("📦 [{}] Traitement par chunks: {} chunks estimés",
                        context.getDocument().getDocumentId(), estimatedChunks);
                result = extractWithChunking(ocrText, context, warnings, metadata);
            } else {
                log.debug("📄 [{}] Traitement direct (pas de chunking nécessaire)",
                        context.getDocument().getDocumentId());
                result = extractDirect(ocrText, context, warnings, metadata);
            }

            // Validation résultat
            double confidence = calculateConfidence(result);
            metadata.put("articlesCount", result.has(ARTICLES_FIELD) ? result.getAsJsonArray(ARTICLES_FIELD).size() : 0);
            metadata.put("ocrLength", ocrText.length());

            log.info("✅ [{}] Extraction JSON terminée: {} articles, confiance: {}",
                    context.getDocument().getDocumentId(),
                    metadata.get("articlesCount"),
                    String.format("%.2f", confidence));

            return TransformationResult.<JsonObject>builder()
                    .output(result)
                    .confidence(confidence)
                    .method(TRANSFORMATION_NAME)
                    .timestamp(Instant.now())
                    .durationMs(System.currentTimeMillis() - startTime)
                    .warnings(warnings)
                    .metadata(metadata)
                    .build();

        } catch (Exception e) {
            log.error("❌ [{}] Erreur transformation OCR → JSON: {}",
                    context.getDocument().getDocumentId(), e.getMessage(), e);
            warnings.add("Erreur fatale: " + e.getMessage());

            return TransformationResult.<JsonObject>builder()
                    .output(new JsonObject())
                    .confidence(0.0)
                    .method(TRANSFORMATION_NAME)
                    .timestamp(Instant.now())
                    .durationMs(System.currentTimeMillis() - startTime)
                    .warnings(warnings)
                    .metadata(metadata)
                    .build();
        }
    }

    @Override
    public boolean canTransform(TransformationContext context) {
        IAProvider provider = context.getProvider();
        if (provider == null || !provider.isAvailable()) {
            log.warn("⚠️ Provider IA non disponible pour OCR_TO_JSON");
            return false;
        }
        return true;
    }

    @Override
    public int estimateChunks(String input, TransformationContext context) {
        if (input == null) return 0;
        
        int maxChunkSize = context.getConfig().getChunkSize();
        if (input.length() <= maxChunkSize) {
            return 1;
        }
        
        // Estimation simple basée sur la taille
        int overlap = context.getConfig().getChunkOverlap();
        int effectiveChunkSize = maxChunkSize - overlap;
        return (int) Math.ceil((double) input.length() / effectiveChunkSize);
    }

    /**
     * Extraction directe sans chunking (texte court)
     */
    private JsonObject extractDirect(String ocrText, TransformationContext context,
                                      List<String> warnings, Map<String, Object> metadata) {
        String prompt = promptLoader.loadPrompt(PROMPT_NAME, ocrText);

        IAProvider.ModelInfo model = context.getProvider()
                .selectBestModel(false, prompt.length() / 4)
                .orElseThrow(() -> new IllegalStateException("Aucun modèle disponible"));
        
        AIRequest request = AIRequest.builder()
                .model(model.name())
                .prompt(prompt)
                .temperature(0.1) // Précision maximale
                .maxTokens(context.getConfig().getMaxTokens())
                .stream(false)
                .build();

        try {
            AIResponse response = context.getProvider().complete(request);
            metadata.put("tokensUsed", response.getTokensUsed());
            metadata.put("processingTimeMs", response.getProcessingTimeMs());

            String jsonText = cleanJsonResponse(response.getGeneratedText());
            JsonObject result = JsonParser.parseString(jsonText).getAsJsonObject();

            log.debug("✅ JSON extrait directement: {} caractères",
                    response.getGeneratedText().length());

            return result;

        } catch (JsonSyntaxException e) {
            log.error("❌ Erreur parsing JSON: {}", e.getMessage());
            warnings.add("JSON invalide retourné par IA: " + e.getMessage());
            return new JsonObject();
        }
    }

    /**
     * Extraction avec chunking pour textes longs
     * Stratégie : Extraire par chunks puis fusionner
     */
    private JsonObject extractWithChunking(String ocrText, TransformationContext context,
                                            List<String> warnings, Map<String, Object> metadata) {
        int maxChunkSize = context.getConfig().getChunkSize();
        int overlap = context.getConfig().getChunkOverlap();
        
        List<String> chunks = textChunker.chunk(ocrText, maxChunkSize, overlap);
        metadata.put("actualChunks", chunks.size());

        // Extraction chunk par chunk
        List<JsonObject> chunkResults = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            log.debug("📦 [{}] Traitement chunk {}/{}: {} chars",
                    context.getDocument().getDocumentId(),
                    i + 1, chunks.size(), chunkText.length());

            try {
                JsonObject chunkResult = extractChunk(chunkText, context, i + 1, chunks.size());
                if (chunkResult != null && chunkResult.has(ARTICLES_FIELD)) {
                    chunkResults.add(chunkResult);
                }
            } catch (Exception e) {
                log.warn("⚠️ Échec chunk {}/{}: {}",
                        i + 1, chunks.size(), e.getMessage());
                warnings.add(String.format("Chunk %d/%d échoué: %s",
                        i + 1, chunks.size(), e.getMessage()));
            }
        }

        // Fusion des résultats
        JsonObject merged = mergeChunkResults(chunkResults);
        metadata.put("chunksProcessed", chunkResults.size());
        metadata.put("chunksFailed", chunks.size() - chunkResults.size());

        return merged;
    }

    /**
     * Extrait un chunk individuel
     */
    private JsonObject extractChunk(String chunkText,
                                     TransformationContext context,
                                     int chunkIndex, int totalChunks) {
        String prompt = promptLoader.loadPrompt(PROMPT_NAME, chunkText);

        IAProvider.ModelInfo model = context.getProvider()
                .selectBestModel(false, prompt.length() / 4)
                .orElseThrow(() -> new IllegalStateException("Aucun modèle disponible"));
        
        AIRequest request = AIRequest.builder()
                .model(model.name())
                .prompt(prompt)
                .temperature(0.1)
                .maxTokens(context.getConfig().getMaxTokens())
                .stream(false)
                .build();

        AIResponse response = context.getProvider().complete(request);
        String jsonText = cleanJsonResponse(response.getGeneratedText());

        try {
            return JsonParser.parseString(jsonText).getAsJsonObject();
        } catch (JsonSyntaxException e) {
            log.warn("⚠️ JSON invalide dans chunk {}/{}", chunkIndex, totalChunks);
            return new JsonObject();
        }
    }

    /**
     * Fusionne les résultats de plusieurs chunks
     */
    private JsonObject mergeChunkResults(List<JsonObject> chunkResults) {
        if (chunkResults.isEmpty()) {
            return new JsonObject();
        }

        // Prendre métadonnées du premier chunk (généralement complètes)
        JsonObject merged = chunkResults.get(0).deepCopy();

        // Fusionner les articles de tous les chunks
        List<JsonObject> allArticles = new ArrayList<>();
        for (JsonObject chunk : chunkResults) {
            if (chunk.has(ARTICLES_FIELD)) {
                chunk.getAsJsonArray(ARTICLES_FIELD).forEach(article ->
                        allArticles.add(article.getAsJsonObject())
                );
            }
        }

        // Dédupliquer par index (garder premier trouvé)
        Map<Integer, JsonObject> uniqueArticles = new HashMap<>();
        for (JsonObject article : allArticles) {
            if (article.has(INDEX_FIELD)) {
                int index = article.get(INDEX_FIELD).getAsInt();
                uniqueArticles.putIfAbsent(index, article);
            }
        }

        // Reconstruire array trié
        merged.add(ARTICLES_FIELD, gson.toJsonTree(
                uniqueArticles.values().stream()
                        .sorted((a, b) -> Integer.compare(
                                a.get(INDEX_FIELD).getAsInt(),
                                b.get(INDEX_FIELD).getAsInt()
                        ))
                        .toList()
        ));

        return merged;
    }

    /**
     * Nettoie la réponse JSON (supprime markdown, commentaires)
     */
    private String cleanJsonResponse(String response) {
        String cleaned = response.trim();

        // Supprime blocs markdown
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }

    /**
     * Calcule confiance basée sur complétude du JSON
     */
    private double calculateConfidence(JsonObject result) {
        double confidence = 0.0;

        // Présence articles (40%)
        if (result.has(ARTICLES_FIELD)) {
            int articlesCount = result.getAsJsonArray(ARTICLES_FIELD).size();
            confidence += Math.min(1.0, articlesCount / 10.0) * 0.40;
        }

        // Métadonnées présentes (30%)
        int metadataFields = 0;
        String[] requiredFields = {"titre", "numero", "annee", "type"};
        for (String field : requiredFields) {
            if (result.has(field) && !result.get(field).isJsonNull()) {
                metadataFields++;
            }
        }
        confidence += (metadataFields / (double) requiredFields.length) * 0.30;

        // Structure valide (20%)
        boolean hasValidStructure = result.has(ARTICLES_FIELD) &&
                result.getAsJsonArray(ARTICLES_FIELD).size() > 0;
        if (hasValidStructure) {
            confidence += 0.20;
        }

        // Signataires (10%)
        if (result.has("signataires") && result.getAsJsonArray("signataires").size() > 0) {
            confidence += 0.10;
        }

        return Math.min(1.0, confidence);
    }
}
