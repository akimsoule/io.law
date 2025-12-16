# Prompts IA - law-ai

Tous les prompts utilisés par les transformations IA sont centralisés ici pour faciliter la maintenance et permettre les ajustements sans recompilation.

## 📁 Structure

```
prompts/
├── README.md                 # Ce fichier
├── ocr-correction.txt        # Correction erreurs OCR
├── ocr-to-json.txt           # Extraction structure JSON depuis OCR
├── json-correction.txt       # Correction valeurs JSON
├── pdf-to-ocr.txt            # Extraction texte depuis images PDF
└── pdf-to-json.txt           # Extraction JSON direct depuis images PDF
```

## 📄 Description des prompts

### ocr-correction.txt
**Usage** : `OcrCorrectionTransformation` (String → String)  
**Objectif** : Corriger les erreurs OCR évidentes sans inventer de contenu  
**Variables** : `%s` = texte OCR à corriger

**Exemple** :
```
Entrée  : "Articlc 1e : La préscnte loi..."
Sortie  : "Article 1er : La présente loi..."
```

### ocr-to-json.txt
**Usage** : `OcrToJsonTransformation` (String → JsonObject)  
**Objectif** : Extraire la structure juridique complète (articles, métadonnées, signataires)  
**Variables** : `%s` = texte OCR corrigé

**Schéma JSON extrait** :
```json
{
  "titre": "Loi n° 2024-15...",
  "numero": 15,
  "annee": 2024,
  "type": "loi",
  "articles": [...],
  "signataires": [...]
}
```

### json-correction.txt
**Usage** : `JsonCorrectionTransformation` (JsonObject → JsonObject)  
**Objectif** : Corriger orthographe/grammaire dans les valeurs JSON sans modifier la structure  
**Variables** : `%s` = JSON à corriger (sérialisé)

**Exemple** :
```json
// Avant
{"titre": "Loi portant réfrome de..."}

// Après
{"titre": "Loi portant réforme de..."}
```

### pdf-to-ocr.txt
**Usage** : `PdfToOcrTransformation` (Path → String)  
**Objectif** : Extraire tout le texte visible depuis des images PDF via vision IA  
**Variables** : `%s` = type, `%s` = année, `%s` = numéro (contexte document)

**Note** : Nécessite modèle avec support vision (llava, llama-vision)

### pdf-to-json.txt
**Usage** : `PdfToJsonTransformation` (Path → JsonObject)  
**Objectif** : Extraction JSON directe depuis images PDF (bypass OCR)  
**Variables** : `%s` = type, `%s` = année, `%s` = numéro (contexte document)

**Avantage** : Plus rapide que pipeline OCR → JSON (1 seul appel IA)

## 🔧 Utilisation

### Depuis le code Java

```java
@Component
@RequiredArgsConstructor
public class MyTransformation implements IATransformation<String, JsonObject> {
    
    private final PromptLoader promptLoader;
    
    @Override
    public TransformationResult<JsonObject> transform(String input, TransformationContext context) {
        // Chargement simple
        String prompt = promptLoader.loadPrompt("ocr-to-json", input);
        
        // Avec variables multiples
        String prompt2 = promptLoader.loadPrompt("pdf-to-ocr", 
                context.getDocument().getType(),
                context.getDocument().getYear(),
                context.getDocument().getNumber()
        );
        
        // Envoyer à l'IA...
    }
}
```

### Chargement et cache

- Le `PromptLoader` charge les prompts depuis `classpath:prompts/*.txt`
- Cache en mémoire pour éviter les rechargements répétés
- Appeler `promptLoader.reloadAll()` pour vider le cache (tests/debug)

## ✏️ Modification des prompts

### Workflow recommandé

1. **Modifier le fichier .txt** concerné
2. **Recompiler le module** : `mvn clean compile -pl law-tojson/law-ai`
3. **Tester la transformation** modifiée
4. **Valider** que les résultats sont meilleurs

**Note** : Les prompts sont intégrés dans le JAR lors de la compilation (`target/classes/prompts/*.txt`)

### Bonnes pratiques

✅ **À FAIRE** :
- Utiliser des instructions claires et strictes
- Interdire explicitement les hallucinations (`N'INVENTE AUCUNE information`)
- Donner des exemples concrets de corrections attendues
- Spécifier le format de sortie exact (JSON, texte brut)

❌ **À ÉVITER** :
- Prompts trop verbeux (coût tokens élevé)
- Instructions ambiguës ou contradictoires
- Demander à l'IA d'interpréter ou deviner
- Formats de sortie complexes ou non structurés

## 📊 Métriques de qualité

### Température optimale par prompt

| Prompt | Température | Raison |
|--------|-------------|--------|
| ocr-correction | 0.1 | Déterministe, corrections précises |
| ocr-to-json | 0.1 | Extraction factuelle stricte |
| json-correction | 0.2 | Légère créativité pour orthographe |
| pdf-to-ocr | 0.0 | Vision pure, aucune interprétation |
| pdf-to-json | 0.1 | Extraction structurée rigoureuse |

### Tokens typiques

| Prompt | Base | + Input (1000 chars) | Total |
|--------|------|---------------------|-------|
| ocr-correction | ~200 | ~250 | ~450 |
| ocr-to-json | ~350 | ~250 | ~600 |
| json-correction | ~180 | ~300 | ~480 |
| pdf-to-ocr | ~150 | images | variable |
| pdf-to-json | ~400 | images | variable |

## 🧪 Tests

### Valider un prompt

```bash
# 1. Modifier le prompt
vim src/main/resources/prompts/ocr-correction.txt

# 2. Recompiler
mvn clean compile -pl law-tojson/law-ai -DskipTests

# 3. Tester avec un document réel
java -jar law-app.jar --job=pdfToJsonJob --doc=loi-2024-15 --force
```

### Comparer prompts (A/B testing)

1. Sauvegarder l'ancien prompt : `cp ocr-correction.txt ocr-correction.old.txt`
2. Modifier le prompt actuel
3. Tester avec les mêmes documents
4. Comparer confiance et qualité résultats

## 📚 Références

- **Service** : [`PromptLoader.java`](../../java/bj/gouv/sgg/ai/service/PromptLoader.java)
- **Transformations** : [`transformation/`](../../java/bj/gouv/sgg/ai/transformation/)
- **Documentation** : [README.md](../../../README.md)

---

**Dernière mise à jour** : 11 décembre 2025
