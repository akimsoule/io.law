# Module law-ocr-cor

## Description

Module de **correction OCR** via Intelligence Artificielle ou corrections programmatiques CSV.

**Nouveau focus** : Corriger le texte OCR brut (pas d'extraction JSON directe).

---

## Architecture

### Flow Global
```
law-pdf-ocr (Tesseract)
    ↓
law-ocr-cor (IA/CSV correction) ← CE MODULE
    ↓
law-ocr-json (Regex parsing)
    ↓
law-json-config (Orchestration)
```

### Responsabilité
- **Input** : Texte OCR brut (avec erreurs "Articlc", "1e", espaces incorrects)
- **Output** : Texte OCR corrigé (erreurs normalisées)
- **Focus** : Correction uniquement, PAS d'extraction JSON

---

## Stratégies de Correction

### Interface `OcrCorrectionStrategy`

```java
public interface OcrCorrectionStrategy {
    String correctOcr(String rawOcrText) throws IAException;
    String getStrategyName();
    boolean isAvailable();
}
```

### 1. IACorrectionStrategy

**Principe** : Correction contextuelle via modèles IA (Ollama/Groq).

**Avantages** :
- ✅ Correction intelligente (comprend le contexte)
- ✅ Gère erreurs non prévues dans CSV
- ✅ Amélioration continue (modèles évoluent)

**Inconvénients** :
- ⚠️ Nécessite IA disponible
- ⚠️ Plus lent que CSV
- ⚠️ Consommation ressources

**Exemple** :
```java
@Component
@RequiredArgsConstructor
public class IACorrectionStrategy implements OcrCorrectionStrategy {
    private final IAService iaService;
    
    @Override
    public String correctOcr(String rawOcrText) throws IAException {
        String prompt = buildCorrectionPrompt(rawOcrText);
        return iaService.correctOcrText(rawOcrText, prompt);
    }
}
```

### 2. CsvCorrectionStrategy

**Principe** : Corrections prédéfinies via fichier CSV (287 entrées actuelles).

**Avantages** :
- ✅ Rapide (regex replace instantané)
- ✅ Prédictible (mêmes corrections)
- ✅ Autonome (pas d'IA externe)
- ✅ Léger (faible ressources)

**Inconvénients** :
- ⚠️ Limité (erreurs connues uniquement)
- ⚠️ Maintenance manuelle CSV
- ⚠️ Pas contextuel

**Exemple corrections.csv** :
```csv
"Articlc","Article"
"1e","1er"
"Artic|e","Article"
"A rticle ","Article"
"ARTICIS Ier","Article 1er"
```

**Exemple** :
```java
@Component
@RequiredArgsConstructor
public class CsvCorrectionStrategy implements OcrCorrectionStrategy {
    private final CsvCorrector csvCorrector;
    
    @Override
    public String correctOcr(String rawOcrText) throws IAException {
        return csvCorrector.correct(rawOcrText);
    }
}
```

---

## Usage

### Sélection Automatique de Stratégie

```java
@Service
@RequiredArgsConstructor
public class OcrCorrectionOrchestrator {
    
    private final IACorrectionStrategy iaStrategy;
    private final CsvCorrectionStrategy csvStrategy;
    
    public String correctOcr(String rawOcrText) {
        // Préférer IA si disponible, sinon CSV
        OcrCorrectionStrategy strategy = iaStrategy.isAvailable() 
            ? iaStrategy 
            : csvStrategy;
        
        log.info("🔧 Utilisation stratégie: {}", strategy.getStrategyName());
        return strategy.correctOcr(rawOcrText);
    }
}
```

### Utilisation dans PdfToJsonProcessor

```java
@Component
@RequiredArgsConstructor
public class PdfToJsonProcessor implements ItemProcessor<LawDocument, LawDocument> {
    
    private final OcrCorrectionService ocrCorrectionService;
    
    @Override
    public LawDocument process(LawDocument document) {
        // 1. Extraire OCR
        String rawOcr = ocrService.extractText(pdfPath);
        
        // 2. Corriger OCR (IA ou CSV)
        JsonResult result = ocrCorrectionService.extractWithAICleanup(document, rawOcr);
        
        // 3. Sauvegarder JSON
        Files.writeString(jsonPath, result.getJson());
        
        return document;
    }
}
```

---

## Services Principaux

### IAService (Interface Simplifiée)

```java
public interface IAService {
    String correctOcrText(String rawOcr, String prompt) throws IAException;
    String getSourceName();
    boolean isAvailable();
}
```

**Implémentations** :
- `OllamaClient` : Correction via Ollama (local)
- `GroqClient` : Correction via Groq (cloud API)
- `NoClient` : Pas d'IA disponible

### OcrCorrectionService

```java
@Service
@RequiredArgsConstructor
public class OcrCorrectionService {
    
    private final OllamaClient ollamaClient;
    private final Gson gson;
    
    public JsonResult extractWithAICleanup(LawDocument doc, String ocrText) {
        // 1. Construire prompt anti-hallucination
        String prompt = buildCleanupPrompt(doc, ocrText);
        
        // 2. Corriger via IA
        String jsonResponse = ollamaClient.complete(prompt);
        
        // 3. Parser et enrichir
        JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
        
        return new JsonResult(gson.toJson(jsonObject), 0.90, "AI-CLEANUP");
    }
}
```

---

## Configuration

### application.yml

```yaml
law:
  capacity:
    ollama-url: http://localhost:11434
    ollama-models-required: gemma3n
```

### Corrections CSV

Fichier : `law-ocr-json/src/main/resources/corrections.csv`

**Format** :
```csv
"erreur OCR","correction"
```

**Stats actuelles** : 287 corrections (8 déc 2025)

---

## Tests

### Test IACorrectionStrategy

```java
@Test
void testIACorrection() {
    String rawOcr = "Articlc 1e : Le présent décret...";
    
    String corrected = iaStrategy.correctOcr(rawOcr);
    
    assertThat(corrected).contains("Article 1er");
}
```

### Test CsvCorrectionStrategy

```java
@Test
void testCsvCorrection() {
    String rawOcr = "Articlc 1e porte...";
    
    String corrected = csvStrategy.correctOcr(rawOcr);
    
    assertThat(corrected).isEqualTo("Article 1er porte...");
}
```

---

## Comparaison Stratégies

| Aspect | IACorrectionStrategy | CsvCorrectionStrategy |
|--------|----------------------|----------------------|
| **Vitesse** | Lent (~5-10s) | Rapide (<1s) |
| **Précision** | Élevée (contextuel) | Moyenne (prédéfini) |
| **Dépendances** | Ollama/Groq | Aucune (autonome) |
| **Ressources** | CPU/GPU intensif | Léger |
| **Maintenance** | Automatique (modèles) | Manuelle (CSV) |
| **Cas d'usage** | Documents complexes | Documents standards |

---

## Évolutions Futures

1. **Stratégie hybride** : IA + CSV combinés
2. **Machine Learning** : Apprentissage des corrections récurrentes
3. **Cache intelligent** : Réutiliser corrections précédentes
4. **Métriques qualité** : Mesurer efficacité chaque stratégie

---

## Références

- **[architecture.md](../guides/architecture.md)** : Vue d'ensemble modules
- **[json-config.md](json-config.md)** : Orchestration extraction
- **[copilot-instructions.md](../../copilot-instructions.md)** : Conventions projet
