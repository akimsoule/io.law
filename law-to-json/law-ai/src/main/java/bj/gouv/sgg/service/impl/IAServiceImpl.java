package bj.gouv.sgg.service.impl;

import bj.gouv.sgg.ai.model.AIRequest;
import bj.gouv.sgg.ai.model.AIResponse;
import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.provider.IAProvider;
import bj.gouv.sgg.provider.IAProviderFactory;
import bj.gouv.sgg.service.IAService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Implémentation de IAService utilisant IAProviderFactory.
 * 
 * <p><b>Responsabilités</b> :
 * <ul>
 *   <li>Sélectionner le meilleur provider disponible (Ollama → Groq → NoIA)</li>
 *   <li>Adapter les appels IAService vers IAProvider</li>
 *   <li>Gérer les erreurs et retries</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IAServiceImpl implements IAService {

    private final IAProviderFactory providerFactory;
    private final Gson gson;

    @Override
    public String correctOcrText(String rawOcrText, String prompt) throws IAException {
        IAProvider provider = providerFactory.selectProvider(false, estimateTokens(rawOcrText));
        
        if (!provider.isAvailable()) {
            throw new IAException("Aucun provider IA disponible pour correction OCR");
        }

        log.debug("🔧 Correction OCR avec provider: {}", provider.getProviderName());

        AIRequest request = AIRequest.builder()
                .prompt(prompt + "\n\nTexte à corriger:\n" + rawOcrText)
                .temperature(0.3) // Faible température pour correction précise
                .maxTokens(8000)
                .build();

        AIResponse response = provider.complete(request);
        return response.getText();
    }

    @Override
    public JsonObject extractJsonFromOcr(String ocrText) throws IAException {
        IAProvider provider = providerFactory.selectProvider(false, estimateTokens(ocrText));
        
        if (!provider.isAvailable()) {
            throw new IAException("Aucun provider IA disponible pour extraction JSON");
        }

        log.debug("📄 Extraction JSON avec provider: {}", provider.getProviderName());

        // Le prompt est géré dans les providers (OllamaProvider, GroqProvider)
        // via JsonSchemaLoader qui charge prompts/ocr-to-json.txt
        AIRequest request = AIRequest.builder()
                .prompt(ocrText)
                .temperature(0.2) // Très faible pour extraction structurée
                .maxTokens(8000)
                .build();

        AIResponse response = provider.complete(request);
        
        // Parser la réponse JSON
        try {
            String jsonText = response.getText();
            if (jsonText == null || jsonText.isBlank()) {
                throw new IAException("Réponse IA vide");
            }
            
            // Nettoyer les éventuels markdown wrappers (```json ... ```)
            jsonText = jsonText.trim();
            if (jsonText.startsWith("```json")) {
                jsonText = jsonText.substring(7);
            }
            if (jsonText.startsWith("```")) {
                jsonText = jsonText.substring(3);
            }
            if (jsonText.endsWith("```")) {
                jsonText = jsonText.substring(0, jsonText.length() - 3);
            }
            jsonText = jsonText.trim();
            
            return gson.fromJson(jsonText, JsonObject.class);
            
        } catch (JsonSyntaxException e) {
            throw new IAException("Réponse IA n'est pas un JSON valide: " + e.getMessage(), e);
        }
    }

    @Override
    public String getSourceName() {
        IAProvider provider = providerFactory.selectProvider(false, 1000);
        return provider.getProviderName();
    }

    @Override
    public boolean isAvailable() {
        return providerFactory.hasAnyProvider();
    }

    @Override
    public int getMaxContextTokens() {
        if (!isAvailable()) {
            return 0;
        }
        
        IAProvider provider = providerFactory.selectProvider(false, 1000);
        
        // Utiliser le contexte du modèle sélectionné
        Optional<IAProvider.ModelInfo> modelInfo = provider.selectBestModel(false, 1000);
        if (modelInfo.isPresent()) {
            return modelInfo.get().contextWindow();
        }
        
        // Fallback sur les capacités générales du provider
        return provider.getCapabilities().maxContextTokens();
    }

    /**
     * Estime le nombre de tokens nécessaires (approximation: 1 token ≈ 4 chars).
     */
    private int estimateTokens(String text) {
        return text == null ? 0 : text.length() / 4;
    }
}
