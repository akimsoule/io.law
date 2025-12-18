package bj.gouv.sgg.util;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gère intelligemment les erreurs 429 (Too Many Requests) avec :
 * - Backoff exponentiel adaptatif
 * - Suivi du taux de 429
 * - Ralentissement automatique quand trop de 429
 * - Retry avec délais progressifs
 */
@Slf4j
public class RateLimitHandler {
    
    // Compteurs pour statistiques
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger rate429Count = new AtomicInteger(0);
    private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());
    
    // Configuration du backoff
    private static final int BASE_DELAY_MS = 2000; // 2 secondes de base
    private static final int MAX_DELAY_MS = 30000; // 30 secondes max
    private static final int MAX_RETRIES = 3;
    
    // Seuils adaptatifs
    private static final double HIGH_429_THRESHOLD = 0.3; // Si > 30% de 429, ralentir
    private static final double CRITICAL_429_THRESHOLD = 0.5; // Si > 50% de 429, pause longue
    
    // Délai adaptatif entre requêtes (volatile pour visibilité entre threads)
    private volatile int currentDelayBetweenRequests = 0;
    
    // Random instance for jitter
    private final Random random = new Random();
    
    public RateLimitHandler() {
        resetStats();
    }
    
    /**
     * Enregistre une requête et applique un délai adaptatif si nécessaire
     */
    public void beforeRequest() {
        totalRequests.incrementAndGet();
        
        // Appliquer le délai adaptatif entre requêtes
        if (currentDelayBetweenRequests > 0) {
            try {
                Thread.sleep(currentDelayBetweenRequests);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Reset des stats toutes les 5 minutes pour adaptation continue
        long now = System.currentTimeMillis();
        long last = lastResetTime.get();
        if (now - last > 300_000 && lastResetTime.compareAndSet(last, now)) { // 5 minutes
            resetStats();
        }
    }
    
    /**
     * Enregistre une réponse 429 et ajuste automatiquement les délais
     */
    public void on429(String url) {
        int count429 = rate429Count.incrementAndGet();
        int total = totalRequests.get();
        double rate = total > 0 ? (double) count429 / total : 0;
        
        log.warn("⚠️ rate-limit-hit url={} count429={} total={} rate={}", 
                 url, count429, total, String.format("%.2f", rate));
        
        // Ajuster le délai adaptatif
        adjustDelayBetweenRequests(rate);
    }
    
    /**
     * Exécute une requête avec retry automatique en cas de 429
     */
    public int executeWithRetry(String url, ProbeFunction probeFunc) {
        int attempt = 0;
        int code;
        
        while (attempt < MAX_RETRIES) {
            beforeRequest();
            code = probeFunc.probe(url);
            
            if (code == 429) {
                on429(url);
                attempt++;
                
                if (attempt < MAX_RETRIES) {
                    int delayMs = calculateBackoffDelay(attempt);
                    log.info("🔄 retry-after-429 url={} attempt={} delayMs={}", url, attempt, delayMs);
                    
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return code;
                    }
                }
            } else {
                // Succès ou erreur définitive (pas 429)
                return code;
            }
        }
        
        // Échec après MAX_RETRIES tentatives
        log.warn("❌ retry-exhausted url={} attempts={}", url, MAX_RETRIES);
        return 429;
    }
    
    /**
     * Calcule le délai de backoff exponentiel pour un retry donné
     */
    private int calculateBackoffDelay(int attemptNumber) {
        // Backoff exponentiel : 2s, 4s, 8s, 16s...
        int delay = BASE_DELAY_MS * (int) Math.pow(2.0, attemptNumber - 1.0);
        
        // Ajouter un jitter aléatoire pour éviter la synchronisation
        int jitter = random.nextInt(1000);
        
        return Math.min(delay + jitter, MAX_DELAY_MS);
    }
    
    /**
     * Ajuste le délai entre requêtes basé sur le taux de 429
     */
    private void adjustDelayBetweenRequests(double rate429) {
        if (rate429 >= CRITICAL_429_THRESHOLD) {
            // > 50% de 429 : pause critique de 5 secondes entre requêtes
            currentDelayBetweenRequests = 5000;
            log.warn("🚨 rate-limit-critical rate={} delayMs={}", 
                     String.format("%.2f", rate429), currentDelayBetweenRequests);
        } else if (rate429 >= HIGH_429_THRESHOLD) {
            // > 30% de 429 : pause élevée de 2 secondes entre requêtes
            currentDelayBetweenRequests = 2000;
            log.warn("⚠️ rate-limit-high rate={} delayMs={}", 
                     String.format("%.2f", rate429), currentDelayBetweenRequests);
        } else if (rate429 > 0.1) {
            // > 10% de 429 : pause modérée de 1 seconde
            currentDelayBetweenRequests = 1000;
            log.info("⏱️ rate-limit-moderate rate={} delayMs={}", 
                     String.format("%.2f", rate429), currentDelayBetweenRequests);
        } else {
            // < 10% de 429 : réduire progressivement le délai
            currentDelayBetweenRequests = Math.max(0, currentDelayBetweenRequests - 500);
            if (currentDelayBetweenRequests == 0) {
                log.info("✅ rate-limit-ok rate={} delayMs=0", String.format("%.2f", rate429));
            }
        }
    }
    
    /**
     * Réinitialise les statistiques
     */
    private void resetStats() {
        totalRequests.set(0);
        rate429Count.set(0);
        lastResetTime.set(System.currentTimeMillis());
        log.info("🔄 stats-reset timestamp={}", Instant.now());
    }
    
    /**
     * Retourne les statistiques actuelles
     */
    public Stats getStats() {
        int total = totalRequests.get();
        int count429 = rate429Count.get();
        double rate = total > 0 ? (double) count429 / total : 0;
        return new Stats(total, count429, rate, currentDelayBetweenRequests);
    }
    
    /**
     * Interface fonctionnelle pour la fonction de probe
     */
    @FunctionalInterface
    public interface ProbeFunction {
        int probe(String url);
    }
    
    /**
     * Classe pour les statistiques
     */
    public static class Stats {
        public final int totalRequests;
        public final int count429;
        public final double rate429;
        public final int currentDelayMs;
        
        public Stats(int totalRequests, int count429, double rate429, int currentDelayMs) {
            this.totalRequests = totalRequests;
            this.count429 = count429;
            this.rate429 = rate429;
            this.currentDelayMs = currentDelayMs;
        }
        
        @Override
        public String toString() {
            return String.format("total=%d 429=%d rate=%.2f delayMs=%d", 
                                totalRequests, count429, rate429, currentDelayMs);
        }
    }
}
