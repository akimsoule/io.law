package bj.gouv.sgg.provider;

import bj.gouv.sgg.provider.impl.GroqProvider;
import bj.gouv.sgg.provider.impl.NoIAProvider;
import bj.gouv.sgg.provider.impl.OllamaProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Factory pour sélectionner automatiquement le meilleur provider IA disponible.
 * 
 * <p><b>Ordre de priorité</b> :
 * <ol>
 *   <li><b>Ollama</b> : Prioritaire si disponible (local, gratuit)</li>
 *   <li><b>Groq</b> : Fallback si Ollama indisponible</li>
 *   <li><b>NoIA</b> : Dernier recours (toutes requêtes échouent)</li>
 * </ol>
 * 
 * <p><b>Stratégie</b> : La factory teste la disponibilité réelle au runtime,
 * permettant un basculement automatique entre providers sans reconfiguration.
 */
@Slf4j
public class IAProviderFactory {

    private final OllamaProvider ollamaProvider;
    private final GroqProvider groqProvider;
    private final NoIAProvider noIAProvider;
    
    public IAProviderFactory(OllamaProvider ollamaProvider,
                            GroqProvider groqProvider,
                            NoIAProvider noIAProvider) {
        this.ollamaProvider = ollamaProvider;
        this.groqProvider = groqProvider;
        this.noIAProvider = noIAProvider;
    }

    /**
     * Sélectionne le meilleur provider disponible pour une tâche donnée.
     * 
     * <p><b>Logique</b> :
     * <ol>
     *   <li>Si requiresVision et aucun provider vision → NoIA</li>
     *   <li>Tester Ollama → utiliser si disponible et compatible</li>
     *   <li>Tester Groq → utiliser si disponible et compatible</li>
     *   <li>Retourner NoIA si aucun provider disponible</li>
     * </ol>
     * 
     * @param requiresVision true si images nécessaires
     * @param estimatedTokens Estimation tokens nécessaires
     * @return Provider optimal
     */
    public IAProvider selectProvider(boolean requiresVision, int estimatedTokens) {
        log.debug("🔍 Selecting IA provider (vision={}, tokens={})", requiresVision, estimatedTokens);
        
        // 1. Tenter Ollama en priorité
        if (ollamaProvider.isAvailable()) {
            var model = ollamaProvider.selectBestModel(requiresVision, estimatedTokens);
            if (model.isPresent()) {
                log.info("✅ Selected provider: OLLAMA (model={})", model.get().name());
                return ollamaProvider;
            }
            log.debug("⚠️ Ollama available but no compatible model");
        }
        
        // 2. Fallback Groq
        if (groqProvider.isAvailable()) {
            var model = groqProvider.selectBestModel(requiresVision, estimatedTokens);
            if (model.isPresent()) {
                log.info("✅ Selected provider: GROQ (model={})", model.get().name());
                return groqProvider;
            }
            log.debug("⚠️ Groq available but no compatible model");
        }
        
        // 3. Aucun provider disponible
        log.warn("❌ No IA provider available (Ollama: {}, Groq: {})", 
                ollamaProvider.isAvailable(), groqProvider.isAvailable());
        return noIAProvider;
    }
    
    /**
     * Retourne le provider par nom.
     * 
     * @param providerName Nom du provider ("OLLAMA", "GROQ", "NO_IA")
     * @return Provider correspondant
     * @throws IllegalArgumentException Si nom inconnu
     */
    public IAProvider getProvider(String providerName) {
        return switch (providerName.toUpperCase()) {
            case "OLLAMA" -> ollamaProvider;
            case "GROQ" -> groqProvider;
            case "NO_IA" -> noIAProvider;
            default -> throw new IllegalArgumentException("Unknown provider: " + providerName);
        };
    }
    
    /**
     * Liste tous les providers disponibles.
     * 
     * @return Liste des providers opérationnels
     */
    public List<IAProvider> getAvailableProviders() {
        return List.of(ollamaProvider, groqProvider, noIAProvider).stream()
                .filter(IAProvider::isAvailable)
                .toList();
    }
    
    /**
     * Vérifie si au moins un provider IA est disponible.
     * 
     * @return true si IA disponible, false si seulement NoIA
     */
    public boolean hasAnyProvider() {
        return ollamaProvider.isAvailable() || groqProvider.isAvailable();
    }
}
