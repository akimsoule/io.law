package bj.gouv.sgg.util;

import bj.gouv.sgg.config.LawProperties;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;

public class IaAvailabilityChecker {
    public static boolean isIaAvailable(LawProperties props) {
        // Vérifier Ollama d'abord
        var cap = props.getCapacity();
        if (cap.getOllamaUrl() != null && !cap.getOllamaUrl().isBlank()) {
            try {
                RestTemplate rt = new RestTemplate();
                rt.getForEntity(cap.getOllamaUrl() + "/api/tags", String.class);
                // Vérifier les modèles requis
                String required = cap.getOllamaModelsRequired();
                if (required == null || required.isBlank()) return true;
                String body = rt.getForObject(cap.getOllamaUrl() + "/api/tags", String.class);
                return Arrays.stream(required.split(","))
                        .map(String::trim)
                        .allMatch(model -> body != null && body.contains(model));
            } catch (org.springframework.web.client.RestClientException e) {
                // Ollama indisponible, on tentera Groq
            }
        }
        // Groq: la disponibilité dépendra de l'appel effectif, on considère ici non-bloquant
        return true;
    }
}
