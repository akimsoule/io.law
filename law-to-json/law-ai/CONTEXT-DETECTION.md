# Détection Automatique du Contexte IA

## Vue d'ensemble

Le système détecte automatiquement la taille du contexte disponible selon le provider IA actif (Ollama ou Groq) et ajuste dynamiquement la taille des chunks pour optimiser le traitement.

## Fonctionnement

### 1. Détection du contexte

```java
// IAService.getMaxContextTokens() interroge le provider actif
int contextTokens = iaService.getMaxContextTokens();
// Retourne: 8192 pour Ollama gemma, 32768 pour Groq llama-3.3-70b
```

### 2. Calcul dynamique

**Formule** : `maxChunkSize = (contextTokens × ratio) × 4`

- **contextTokens** : Taille du contexte du modèle (ex: 8192, 32768)
- **ratio** : Ratio de sécurité (0.7 = 70% du contexte)
- **4** : Approximation chars/token

### 3. Exemples de calcul

| Provider | Modèle | Context | Ratio | Max Chunk |
|----------|--------|---------|-------|-----------|
| Ollama | gemma | 8,192 tokens | 70% | ~23K chars |
| Ollama | mixtral | 32,768 tokens | 70% | ~92K chars |
| Groq | llama-3.3-70b | 32,768 tokens | 70% | ~92K chars |
| Groq | llama-3.2-90b-vision | 8,192 tokens | 70% | ~23K chars |
| Fallback | N/A | N/A | N/A | 8,000 chars |

## Configuration

```yaml
# application.yml
batch:
  ai:
    max-chunk-size: 8000          # Fallback si contexte inconnu
    context-usage-ratio: 0.7      # Utiliser 70% du contexte
    chunk-overlap: 200            # Overlap entre chunks
```

### Paramètres

- **max-chunk-size** : Valeur de fallback si le provider ne fournit pas d'info de contexte
- **context-usage-ratio** : Pourcentage du contexte à utiliser (0.0 à 1.0)
  - **0.7** recommandé : garde 30% de marge pour les métadonnées et la réponse
  - **0.8** agressif : utilise plus de contexte, risque de dépassement
  - **0.5** conservateur : sécuritaire mais sous-utilise le contexte
- **chunk-overlap** : Nombre de caractères de chevauchement entre chunks

## Avantages

1. **Auto-adaptation** : S'ajuste automatiquement selon le modèle utilisé
2. **Performance optimale** : Utilise au maximum le contexte disponible
3. **Sécurité** : Ratio de 70% évite les dépassements
4. **Flexibilité** : Passe d'Ollama (8K) à Groq (32K) sans configuration
5. **Graceful degradation** : Fallback sur valeur par défaut si détection échoue

## Workflow

```
Document OCR (50K chars)
    ↓
AiProcessor.process()
    ↓
calculateMaxChunkSize()
    ↓ détecte provider actif
    ↓
Groq llama-3.3-70b détecté: 32768 tokens
    ↓ calcul: 32768 × 0.7 × 4 = 91,750 chars
    ↓
needsChunking(50K, 91K) = false
    ↓ 50K < 91K → pas de chunking nécessaire
    ↓
processSingleText() → traitement direct
```

## Cas d'usage

### Petit document (< contexte)
- **Détection** : 5,000 chars, contexte 23K chars
- **Action** : Traitement direct sans chunking
- **Avantage** : Pas de découpe inutile, contexte préservé

### Document moyen (≈ contexte)
- **Détection** : 80,000 chars, contexte 92K chars (Groq)
- **Action** : Traitement direct sans chunking
- **Avantage** : Exploite pleinement le grand contexte de Groq

### Gros document (> contexte)
- **Détection** : 150,000 chars, contexte 23K chars (Ollama)
- **Action** : Découpage en ~7 chunks de 23K chars
- **Avantage** : Permet le traitement malgré contexte limité

## Logs

```
📊 Contexte détecté: 32768 tokens → max chunk: 91750 chars (ratio: 70%)
🤖 Amélioration IA pour decret-2024-150 (OCR: 85432 chars)
✅ Document traité sans chunking (85K < 92K)
```

```
📊 Contexte détecté: 8192 tokens → max chunk: 22937 chars (ratio: 70%)
🤖 Amélioration IA pour loi-2024-025 (OCR: 156789 chars)
📦 Document trop volumineux (156789 chars), découpage en chunks (max: 22937)
✅ Text chunked: 156789 chars → 7 chunks
```

## Code

### IAService.java
```java
/**
 * Retourne la taille maximale du contexte du provider actif (en tokens).
 */
int getMaxContextTokens();
```

### IAServiceImpl.java
```java
@Override
public int getMaxContextTokens() {
    IAProvider provider = providerFactory.selectProvider(false, 1000);
    Optional<ModelInfo> modelInfo = provider.selectBestModel(false, 1000);
    return modelInfo.isPresent() ? modelInfo.get().contextWindow() 
                                  : provider.getCapabilities().maxContextTokens();
}
```

### AiProcessor.java
```java
private int calculateMaxChunkSize() {
    int contextTokens = iaService.getMaxContextTokens();
    if (contextTokens <= 0) {
        return fallbackMaxChunkSize;
    }
    return (int) (contextTokens * contextUsageRatio * 4);
}
```

## Tests

✅ Tous les tests d'intégration passent (5/5)
✅ Détection automatique fonctionnelle
✅ Fallback opérationnel si provider non disponible
✅ Chunking adaptatif selon contexte détecté
