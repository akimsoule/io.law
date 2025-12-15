package bj.gouv.sgg.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Service de chargement des prompts depuis les ressources.
 * 
 * <p>Les prompts sont stockés dans src/main/resources/prompts/*.txt
 * pour faciliter la maintenance sans recompilation.
 * 
 * <p><b>Prompts disponibles</b> :
 * <ul>
 *   <li>ocr-correction.txt : Correction erreurs OCR</li>
 *   <li>ocr-to-json.txt : Extraction structure JSON depuis OCR</li>
 *   <li>json-correction.txt : Correction valeurs JSON</li>
 *   <li>pdf-to-ocr.txt : Extraction texte depuis images PDF</li>
 *   <li>pdf-to-json.txt : Extraction JSON directe depuis images PDF</li>
 * </ul>
 */
@Service
@Slf4j
public class PromptLoader {

    private final Map<String, String> promptCache = new HashMap<>();
    
    private static final String PROMPTS_DIR = "prompts/";
    
    /**
     * Charge un prompt depuis les ressources.
     * 
     * @param promptName Nom du fichier sans extension (ex: "ocr-correction")
     * @return Contenu du prompt
     * @throws IllegalStateException Si le prompt n'existe pas
     */
    public String loadPrompt(String promptName) {
        // Cache pour éviter de recharger à chaque fois
        return promptCache.computeIfAbsent(promptName, this::loadPromptFromFile);
    }
    
    /**
     * Charge un prompt avec substitution de variables.
     * 
     * <p>Utilise String.format() pour insérer les valeurs.
     * 
     * @param promptName Nom du prompt
     * @param args Variables à substituer dans le prompt
     * @return Prompt avec variables substituées
     */
    public String loadPrompt(String promptName, Object... args) {
        String template = loadPrompt(promptName);
        return String.format(template, args);
    }
    
    /**
     * Charge le contenu d'un fichier prompt depuis resources.
     */
    private String loadPromptFromFile(String promptName) {
        String path = PROMPTS_DIR + promptName + ".txt";
        
        try {
            ClassPathResource resource = new ClassPathResource(path);
            
            if (!resource.exists()) {
                throw new IllegalStateException("Prompt introuvable: " + path);
            }
            
            byte[] bytes = resource.getInputStream().readAllBytes();
            String content = new String(bytes, StandardCharsets.UTF_8);
            
            log.debug("✅ Prompt chargé: {} ({} caractères)", path, content.length());
            
            return content;
            
        } catch (IOException e) {
            throw new IllegalStateException("Erreur chargement prompt: " + path, e);
        }
    }
    
    /**
     * Recharge tous les prompts (vide le cache).
     * Utile pour les tests ou le hot-reload.
     */
    public void reloadAll() {
        promptCache.clear();
        log.info("🔄 Cache des prompts vidé");
    }
    
    /**
     * Liste tous les prompts disponibles.
     */
    public String[] getAvailablePrompts() {
        return new String[]{
            "ocr-correction",
            "ocr-to-json",
            "json-correction",
            "pdf-to-ocr",
            "pdf-to-json"
        };
    }
}
