# Test d'adaptation automatique des threads

## Comportement implémenté

Le système adapte **automatiquement** le nombre de threads aux capacités de la machine, même si une valeur élevée est configurée.

## Exemples de comportement

### Raspberry Pi (4 CPU disponibles)
```yaml
batch.fetch.thread-pool-size: 10  # Demandé
```
**Résultat** : Utilise **4 threads** (min(10, 4) = 4)
```
✅ FetchTaskExecutor initialisé avec 4 threads (CPU: 4, demandé: 10, mode: configuré)
```

### Serveur puissant (32 CPU disponibles)
```yaml
batch.fetch.thread-pool-size: 10  # Demandé
```
**Résultat** : Utilise **10 threads** (min(10, 32) = 10)
```
✅ FetchTaskExecutor initialisé avec 10 threads (CPU: 32, demandé: 10, mode: configuré)
```

### Machine de développement (16 CPU) - Mode AUTO
```yaml
batch.fetch.thread-pool-size: 0  # Auto
```
**Résultat** : Utilise **8 threads** (min(16, 8) = 8, plafonné pour éviter surcharge)
```
✅ FetchTaskExecutor initialisé avec 8 threads (CPU: 16, demandé: auto, mode: auto)
```

### Raspberry Pi Zero (1 CPU disponible)
```yaml
batch.fetch.thread-pool-size: 10  # Demandé
```
**Résultat** : Utilise **1 thread** (min(10, 1) = 1)
```
✅ FetchTaskExecutor initialisé avec 1 thread (CPU: 1, demandé: 10, mode: configuré)
```

## Règles de calcul

```java
int availableProcessors = Runtime.getRuntime().availableProcessors();
int maxUsableProcessors = Math.max(availableProcessors - 1, 1); // Réserver 1 CPU pour le système

if (configuredThreadPoolSize > 0) {
    // Mode configuré : respecte la config MAIS plafonne aux CPU-1
    threadPoolSize = Math.min(configuredThreadPoolSize, maxUsableProcessors);
} else {
    // Mode auto : min(CPU-1, 8) pour éviter surcharge
    threadPoolSize = Math.min(maxUsableProcessors, 8);
}

// Garantir minimum 1 thread
threadPoolSize = Math.max(threadPoolSize, 1);
```

**Nouvelle règle** : Réserve toujours **1 CPU pour le système** (sauf si 1 seul CPU disponible).

### Exemples mis à jour

- **Raspberry Pi 4 CPU** + config=10 → utilise **3 threads** (min(10, 4-1) = 3)
- **Raspberry Pi Zero 1 CPU** + config=10 → utilise **1 thread** (max(1-1, 1) = 1)
- **Desktop 16 CPU** + config=0 (auto) → utilise **8 threads** (min(16-1, 8) = 8)
- **Serveur 32 CPU** + config=10 → utilise **10 threads** (min(10, 32-1) = 10)

## Avantages

✅ **Protection automatique** : Ne surchargera jamais la machine  
✅ **Portable** : Même config fonctionne sur Raspberry Pi et serveur  
✅ **Optimisé** : S'adapte automatiquement aux ressources disponibles  
✅ **Sûr** : Minimum 1 thread garanti  
✅ **Transparent** : Log détaillé du choix effectué

## Test de vérification

```bash
# Machine actuelle (16 CPU)
mvn test -pl law-fetch -Dtest=FetchJobIntegrationTest 2>&1 | grep FetchTaskExecutor
```

**Résultat attendu** :
```
✅ FetchTaskExecutor initialisé avec 4 threads (CPU: 16, demandé: 4, mode: configuré)
```
