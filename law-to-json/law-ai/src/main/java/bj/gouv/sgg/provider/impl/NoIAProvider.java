package bj.gouv.sgg.provider.impl;

import bj.gouv.sgg.ai.model.AIRequest;
import bj.gouv.sgg.ai.model.AIResponse;
import bj.gouv.sgg.provider.IAProvider;
import bj.gouv.sgg.exception.IAException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * Provider fallback quand aucune IA n'est disponible.
 * 
 * <p><b>Usage</b> : Retourné par IAProviderFactory quand ni Ollama ni Groq
 * ne sont disponibles. Toutes les méthodes échouent avec message explicite.
 */
@Slf4j
public class NoIAProvider implements IAProvider {

    @Override
    public String getProviderName() {
        return "NO_IA";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public AIResponse complete(AIRequest request) throws IAException {
        throw new IAException("No AI provider available. Configure Ollama or Groq.");
    }

    @Override
    public ProviderCapabilities getCapabilities() {
        return new ProviderCapabilities(false, false, 0, 0, 0);
    }

    @Override
    public List<ModelInfo> getAvailableModels() {
        return List.of();
    }

    @Override
    public Optional<ModelInfo> selectBestModel(boolean requiresVision, int estimatedTokens) {
        return Optional.empty();
    }
}
