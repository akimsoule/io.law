package bj.gouv.sgg.qa.service.impl;

import bj.gouv.sgg.qa.service.UnrecognizedWordsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implémentation du service de gestion des mots non reconnus.
 * Thread-safe avec ConcurrentHashMap.
 */
@Slf4j
@Service
public class UnrecognizedWordsServiceImpl implements UnrecognizedWordsService {
    
    private static final String DEFAULT_WORDS_FILE = "data/word_non_recognize.txt";
    private Path wordFilePath;
    private final Set<String> knownUnrecognizedWords = ConcurrentHashMap.newKeySet();
    
    public UnrecognizedWordsServiceImpl() {
        this.wordFilePath = Paths.get(DEFAULT_WORDS_FILE);
        loadExistingWords();
    }
    
    /**
     * Définit le chemin du fichier (utile pour les tests)
     */
    public void setWordFilePath(Path path) {
        this.wordFilePath = path;
    }
    
    @Override
    public Set<String> loadExistingWords() {
        Path path = wordFilePath;
        if (Files.exists(path)) {
            try {
                List<String> words = Files.readAllLines(path, StandardCharsets.UTF_8);
                knownUnrecognizedWords.addAll(words.stream()
                    .map(String::trim)
                    .filter(w -> !w.isEmpty())
                    .collect(Collectors.toSet()));
                log.info("✅ Loaded {} existing unrecognized words from {}", knownUnrecognizedWords.size(), wordFilePath);
            } catch (IOException e) {
                log.warn("⚠️ Could not load existing unrecognized words: {}", e.getMessage());
            }
        }
        return Set.copyOf(knownUnrecognizedWords);
    }
    
    @Override
    public void recordUnrecognizedWords(Set<String> unrecognizedWords, String documentId) {
        if (unrecognizedWords == null || unrecognizedWords.isEmpty()) {
            return;
        }
        
        // Filtrer uniquement les nouveaux mots
        Set<String> newWords = unrecognizedWords.stream()
            .filter(w -> !knownUnrecognizedWords.contains(w))
            .collect(Collectors.toSet());
        
        if (newWords.isEmpty()) {
            log.debug("📝 [{}] No new unrecognized words to record", documentId);
            return;
        }
        
        // Ajouter à la mémoire
        knownUnrecognizedWords.addAll(newWords);
        
        // Écrire dans le fichier (append mode)
        try {
            Path path = wordFilePath;
            Files.createDirectories(path.getParent());
            
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, 
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (String word : newWords.stream().sorted().toList()) {
                    writer.write(word);
                    writer.newLine();
                }
            }
            
            log.info("📝 [{}] Recorded {} new unrecognized words (total: {})", 
                    documentId, newWords.size(), knownUnrecognizedWords.size());
            
            if (log.isDebugEnabled() && newWords.size() <= 10) {
                log.debug("New words: {}", newWords);
            }
            
        } catch (IOException e) {
            log.error("❌ Failed to write unrecognized words to {}: {}", wordFilePath, e.getMessage());
        }
    }
    
    @Override
    public double calculateUnrecognizedPenalty(double unrecognizedRate, int totalUnrecognized) {
        // Pénalité progressive basée sur le taux
        // 0-10% : pénalité faible (0.0 - 0.2)
        // 10-30% : pénalité modérée (0.2 - 0.5)
        // 30-50% : pénalité forte (0.5 - 0.8)
        // >50% : pénalité maximale (0.8 - 1.0)
        
        double ratePenalty;
        if (unrecognizedRate <= 0.10) {
            ratePenalty = unrecognizedRate * 2.0;
        } else if (unrecognizedRate <= 0.30) {
            ratePenalty = 0.2 + (unrecognizedRate - 0.10) * 1.5;
        } else if (unrecognizedRate <= 0.50) {
            ratePenalty = 0.5 + (unrecognizedRate - 0.30) * 1.5;
        } else {
            ratePenalty = Math.min(1.0, 0.8 + (unrecognizedRate - 0.50) * 0.4);
        }
        
        // Pénalité additionnelle si nombre absolu élevé
        double countPenalty = 0.0;
        if (totalUnrecognized > 100) {
            countPenalty += 0.05;
        }
        if (totalUnrecognized > 200) {
            countPenalty += 0.05;
        }
        
        return Math.min(1.0, ratePenalty + countPenalty);
    }
    
    @Override
    public int getTotalUnrecognizedWordsCount() {
        return knownUnrecognizedWords.size();
    }
}
