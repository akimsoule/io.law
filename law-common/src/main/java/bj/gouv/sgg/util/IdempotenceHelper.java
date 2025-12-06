package bj.gouv.sgg.util;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Helper générique pour gérer l'idempotence des opérations de traitement.
 * Permet de vérifier si un résultat existe déjà et s'il doit être retraité.
 * 
 * @param <T> Type du résultat (ex: JsonResult, ExtractionResult, etc.)
 */
@Slf4j
public class IdempotenceHelper<T> {
    
    private final Function<Path, Optional<T>> reader;
    private final BiPredicate<T, T> shouldReprocess;
    
    /**
     * Crée un helper d'idempotence.
     * 
     * @param reader Fonction pour lire un résultat existant depuis un chemin
     * @param shouldReprocess Prédicat pour déterminer si on doit retraiter (existing, nouveau) -> boolean
     */
    public IdempotenceHelper(Function<Path, Optional<T>> reader, 
                            BiPredicate<T, T> shouldReprocess) {
        this.reader = reader;
        this.shouldReprocess = shouldReprocess;
    }
    
    /**
     * Lit un résultat existant depuis le disque.
     * 
     * @param path Chemin du fichier à lire
     * @return Optional contenant le résultat existant, ou empty si absent/illisible
     */
    public Optional<T> readExisting(Path path) {
        try {
            return reader.apply(path);
        } catch (Exception e) {
            log.warn("Failed to read existing result from {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Détermine si on doit retraiter en comparant l'existant avec le nouveau candidat.
     * 
     * @param existing Résultat existant (peut être null)
     * @param candidate Nouveau résultat candidat
     * @return true si on doit retraiter (écraser l'existant), false sinon
     */
    public boolean shouldReprocess(Optional<T> existing, T candidate) {
        if (existing.isEmpty()) {
            return true; // Pas de résultat existant, on traite
        }
        
        try {
            return shouldReprocess.test(existing.get(), candidate);
        } catch (Exception e) {
            log.warn("Error comparing results, defaulting to reprocess: {}", e.getMessage());
            return true; // En cas d'erreur, on préfère retraiter
        }
    }
    
    /**
     * Opération complète d'idempotence: lit l'existant et décide si on doit retraiter.
     * 
     * @param path Chemin du fichier résultat
     * @param candidate Nouveau résultat candidat
     * @return true si on doit effectuer le traitement, false si on peut réutiliser l'existant
     */
    public boolean needsProcessing(Path path, T candidate) {
        Optional<T> existing = readExisting(path);
        return shouldReprocess(existing, candidate);
    }
    
    // ============= Factory Methods =============
    
    /**
     * Crée un helper qui compare des résultats avec un score de confiance.
     * Retraite si le nouveau score est meilleur que l'existant + threshold.
     * 
     * @param reader Fonction de lecture
     * @param confidenceExtractor Fonction pour extraire le score de confiance
     * @param threshold Seuil de différence minimum pour retraiter (ex: 0.1)
     * @param <T> Type du résultat
     * @return Helper configuré pour la comparaison de confiance
     */
    public static <T> IdempotenceHelper<T> byConfidence(
            Function<Path, Optional<T>> reader,
            Function<T, Double> confidenceExtractor,
            double threshold) {
        
        BiPredicate<T, T> shouldReprocess = (existing, candidate) -> {
            double existingScore = confidenceExtractor.apply(existing);
            double candidateScore = confidenceExtractor.apply(candidate);
            return candidateScore > existingScore + threshold;
        };
        
        return new IdempotenceHelper<>(reader, shouldReprocess);
    }
    
    /**
     * Crée un helper qui ne retraite jamais si un résultat existe.
     * Utile pour les opérations vraiment idempotentes.
     * 
     * @param reader Fonction de lecture
     * @param <T> Type du résultat
     * @return Helper qui ne retraite jamais
     */
    public static <T> IdempotenceHelper<T> neverReprocess(Function<Path, Optional<T>> reader) {
        return new IdempotenceHelper<>(reader, (existing, candidate) -> false);
    }
    
    /**
     * Crée un helper qui retraite toujours.
     * Utile pour forcer le retraitement même si un résultat existe.
     * 
     * @param reader Fonction de lecture
     * @param <T> Type du résultat
     * @return Helper qui retraite toujours
     */
    public static <T> IdempotenceHelper<T> alwaysReprocess(Function<Path, Optional<T>> reader) {
        return new IdempotenceHelper<>(reader, (existing, candidate) -> true);
    }
}
