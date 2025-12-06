package bj.gouv.sgg.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Cache thread-safe pour patterns regex compilés
 * Améliore les performances en évitant la recompilation répétée des mêmes patterns
 */
@Slf4j
public class PatternCache {
    
    private static final ConcurrentHashMap<String, Pattern> cache = new ConcurrentHashMap<>();
    
    /**
     * Récupère un pattern compilé depuis le cache ou le compile et le met en cache
     * 
     * @param regex Expression régulière à compiler
     * @param flags Flags de compilation (Pattern.CASE_INSENSITIVE, etc.)
     * @return Pattern compilé
     */
    public static Pattern get(String regex, int flags) {
        String key = regex + ":" + flags;
        return cache.computeIfAbsent(key, k -> {
            log.debug("Compiling and caching pattern: {}", regex);
            return Pattern.compile(regex, flags);
        });
    }
    
    /**
     * Récupère un pattern compilé depuis le cache ou le compile (sans flags)
     * 
     * @param regex Expression régulière à compiler
     * @return Pattern compilé
     */
    public static Pattern get(String regex) {
        return get(regex, 0);
    }
    
    /**
     * Précharge un pattern critique au démarrage de l'application
     * 
     * @param name Nom descriptif du pattern pour le logging
     * @param regex Expression régulière à compiler
     * @param flags Flags de compilation
     */
    public static void preload(String name, String regex, int flags) {
        Pattern p = Pattern.compile(regex, flags);
        cache.put(regex + ":" + flags, p);
        log.info("Preloaded pattern: {}", name);
    }
    
    /**
     * Retourne le nombre de patterns en cache
     * 
     * @return Taille du cache
     */
    public static int getCacheSize() {
        return cache.size();
    }
    
    /**
     * Nettoie le cache (utile pour les tests)
     */
    public static void clear() {
        cache.clear();
        log.debug("Pattern cache cleared");
    }
    
    // Prevent instantiation
    private PatternCache() {}
}
