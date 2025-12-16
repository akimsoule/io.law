package bj.gouv.sgg.service;

import bj.gouv.sgg.config.AppConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service pour gérer les mots non reconnus par le dictionnaire.
 * Enregistre les mots dans un fichier unique pour analyse ultérieure.
 * Utilise des méthodes statiques (utility class).
 */
@Slf4j
public final class UnrecognizedWordsService {
    
    private static final Path WORD_FILE_PATH = AppConfig.get().getUnrecognizedWordsFile();
    private static final Set<String> KNOWN_UNRECOGNIZED_WORDS = ConcurrentHashMap.newKeySet();
    
    static {
        loadExistingWords();
    }
    
    private UnrecognizedWordsService() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Définit le chemin du fichier (utile pour les tests)
     */
    public static void setWordFilePath(Path path) {
        KNOWN_UNRECOGNIZED_WORDS.clear();
        loadExistingWordsFromPath(path);
    }
    
    /**
     * Charge les mots déjà connus depuis le fichier
     */
    private static void loadExistingWords() {
        loadExistingWordsFromPath(WORD_FILE_PATH);
    }
    
    private static void loadExistingWordsFromPath(Path path) {
        if (Files.exists(path)) {
            try {
                List<String> words = Files.readAllLines(path, StandardCharsets.UTF_8);
                KNOWN_UNRECOGNIZED_WORDS.addAll(words.stream()
                    .map(String::trim)
                    .filter(w -> !w.isEmpty())
                    .collect(Collectors.toSet()));
                log.info("✅ Loaded {} existing unrecognized words from {}", KNOWN_UNRECOGNIZED_WORDS.size(), path);
            } catch (IOException e) {
                log.warn("⚠️ Could not load existing unrecognized words: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Enregistre les mots non reconnus (uniquement les nouveaux)
     */
    public static void recordUnrecognizedWords(Set<String> unrecognizedWords, String documentId) {
        if (unrecognizedWords == null || unrecognizedWords.isEmpty()) {
            return;
        }
        
        // Filtrer uniquement les nouveaux mots
        Set<String> newWords = unrecognizedWords.stream()
            .filter(w -> !KNOWN_UNRECOGNIZED_WORDS.contains(w))
            .collect(Collectors.toSet());
        
        if (newWords.isEmpty()) {
            log.debug("📝 [{}] No new unrecognized words to record", documentId);
            return;
        }
        
        // Ajouter à la mémoire
        KNOWN_UNRECOGNIZED_WORDS.addAll(newWords);
        
        // Écrire dans le fichier (append mode)
        try {
            Files.createDirectories(WORD_FILE_PATH.getParent());
            
            try (BufferedWriter writer = Files.newBufferedWriter(WORD_FILE_PATH, 
                    StandardCharsets.UTF_8, 
                    StandardOpenOption.CREATE, 
                    StandardOpenOption.APPEND)) {
                for (String word : newWords) {
                    writer.write(word);
                    writer.newLine();
                }
            }
            
            log.info("📝 [{}] Recorded {} new unrecognized words (total: {})", 
                    documentId, newWords.size(), KNOWN_UNRECOGNIZED_WORDS.size());
            
            if (log.isDebugEnabled() && newWords.size() <= 10) {
                log.debug("New words: {}", newWords);
            }
            
        } catch (IOException e) {
            log.error("❌ Failed to write unrecognized words to {}: {}", WORD_FILE_PATH, e.getMessage());
        }
    }
    
    /**
     * Calcule un score de pénalité basé sur le taux de mots non reconnus.
     * 
     * @param unrecognizedRate Taux de mots non reconnus (0.0 - 1.0)
     * @param totalUnrecognized Nombre total de mots non reconnus
     * @return Score de pénalité (0.0 = aucune pénalité, 1.0 = pénalité maximale)
     */
    public static double calculateUnrecognizedPenalty(double unrecognizedRate, int totalUnrecognized) {
        // Pénalité progressive basée sur le taux
        double ratePenalty;
        if (unrecognizedRate <= 0.10) {
            ratePenalty = unrecognizedRate * 2.0; // 0-10% → 0-0.2
        } else if (unrecognizedRate <= 0.30) {
            ratePenalty = 0.2 + (unrecognizedRate - 0.10) * 1.5; // 10-30% → 0.2-0.5
        } else if (unrecognizedRate <= 0.50) {
            ratePenalty = 0.5 + (unrecognizedRate - 0.30) * 1.5; // 30-50% → 0.5-0.8
        } else {
            ratePenalty = Math.min(1.0, 0.8 + (unrecognizedRate - 0.50) * 0.4); // >50% → 0.8-1.0
        }
        
        // Pénalité additionnelle si nombre absolu élevé
        double countPenalty = Math.min(0.2, totalUnrecognized / 100.0 * 0.2);
        
        return Math.min(1.0, ratePenalty + countPenalty);
    }
    
    /**
     * Retourne le nombre total de mots non reconnus connus
     */
    public static int getTotalKnownUnrecognizedWords() {
        return KNOWN_UNRECOGNIZED_WORDS.size();
    }
    
    /**
     * Vérifie si un mot est déjà connu comme non reconnu
     */
    public static boolean isKnownUnrecognized(String word) {
        return KNOWN_UNRECOGNIZED_WORDS.contains(word);
    }
}
