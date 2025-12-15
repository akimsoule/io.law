# Transformation PDF → JSON avec Stratégie de Fallback en Cascade

## Vue d'ensemble

Depuis le **12 décembre 2025**, le module `law-json-config` utilise une stratégie de transformation intelligente avec fallback en cascade, orchestrée par `LawTransformationService`.

Cette approche garantit la meilleure qualité d'extraction possible en combinant **OCR programmatique**, **AI correction**, et **validation qualité** via `law-qa`.

---

## 🎯 Objectifs

1. **Maximiser la qualité** : Utiliser plusieurs stratégies jusqu'à atteindre un seuil de qualité acceptable
2. **Résilience** : Fallback automatique si une stratégie échoue
3. **Traçabilité** : Logs détaillés à chaque étape
4. **Amélioration continue** : Validation qualité via `law-qa` après chaque transformation

---

## 🔄 Pipeline de Transformation

```
┌──────────────────────────────────────────────────────────────┐
│  PDF Document                                                 │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  ÉTAPE 1 : Extraction OCR + Corrections CSV                  │
│  ├─ OcrTransformer.transform()                               │
│  ├─ Applique corrections.csv (287 entrées)                   │
│  ├─ Extrait articles via regex patterns                      │
│  └─ Calcule confiance (dictionnaire + séquence + termes)     │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  CHECK QUALITÉ OCR (law-qa)                                  │
│  ├─ OcrQualityService.calculateConfidence()                  │
│  ├─ Seuil: ${law.quality.ocr-threshold} (défaut: 0.3)       │
│  └─ Si < seuil → ÉTAPE 2, sinon → ÉTAPE 3                   │
└───────────────────────────┬──────────────────────────────────┘
                            │
                ┌───────────┴───────────┐
                │ Confiance < 0.3 ?     │
                └───────────┬───────────┘
                            │
            OUI ────────────┤────────────── NON (skip étape 2)
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  ÉTAPE 2 : AI Correction OCR (optionnel)                    │
│  ├─ OcrCorrectionService.extractWithAICleanup()             │
│  ├─ Corrige erreurs OCR via Ollama/Groq                     │
│  ├─ Re-extrait articles depuis OCR corrigé                  │
│  └─ Compare confiance : garde meilleur résultat             │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  ÉTAPE 3 : Extraction Articles                               │
│  ├─ Articles extraits via regex ou AI                        │
│  └─ JSON structuré généré                                    │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  CHECK QUALITÉ JSON (law-qa)                                 │
│  ├─ JsonQualityService.calculateJsonQualityScore()          │
│  ├─ Seuil: ${law.quality.json-threshold} (défaut: 0.5)      │
│  └─ Si < seuil → ÉTAPE 4, sinon → ÉTAPE 6 (SUCCESS)         │
└───────────────────────────┬──────────────────────────────────┘
                            │
                ┌───────────┴───────────┐
                │ Qualité JSON < 0.5 ?  │
                └───────────┬───────────┘
                            │
            OUI ────────────┤────────────── NON (skip étapes 4-5)
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  ÉTAPE 4 : AI Correction JSON (TODO)                        │
│  ├─ AI améliore JSON existant                               │
│  ├─ Complète métadonnées manquantes                         │
│  └─ Compare qualité : garde meilleur résultat               │
└───────────────────────────┬──────────────────────────────────┘
                            │
                ┌───────────┴───────────┐
                │ Qualité JSON < 0.5 ?  │
                └───────────┬───────────┘
                            │
            OUI ────────────┤────────────── NON (SUCCESS)
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  ÉTAPE 5 : AI Extraction Complète (TODO)                    │
│  ├─ AI lit PDF directement                                   │
│  ├─ Génère JSON complet sans passer par OCR                 │
│  └─ Compare qualité : garde meilleur résultat               │
└───────────────────────────┬──────────────────────────────────┘
                            │
                ┌───────────┴───────────┐
                │ Qualité JSON < 0.5 ?  │
                └───────────┬───────────┘
                            │
            OUI ────────────┤────────────── NON (SUCCESS)
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  ÉTAPE 6 : Vérification Finale                               │
│  ├─ Si qualité >= seuil → SUCCESS (EXTRACTED)               │
│  └─ Si qualité < seuil → FAILED (skip traitement)           │
└──────────────────────────────────────────────────────────────┘
```

---

## 📊 Services Impliqués

### `LawTransformationService`
**Responsabilité** : Orchestration du pipeline complet avec checks qualité

**Méthodes** :
- `transform(LawDocument, Path)` : Point d'entrée principal
- `transformWithOcr()` : Étape 1 - OCR de base
- `transformWithAiOcrCorrection()` : Étape 2 - AI correction OCR
- `transformWithAiJsonCorrection()` : Étape 4 - AI correction JSON (TODO)
- `transformWithAiFull()` : Étape 5 - AI extraction complète (TODO)
- `calculateJsonQuality()` : Validation via law-qa

### `OcrTransformer`
**Responsabilité** : Extraction OCR programmatique + corrections CSV

**Pipeline** :
1. PDF → Texte OCR (Tesseract)
2. Applique `corrections.csv` (287 entrées)
3. Extrait articles via regex patterns
4. Calcule confiance (5 facteurs pondérés)

### `OcrCorrectionService`
**Responsabilité** : Correction OCR via IA (Ollama/Groq)

**Stratégie** :
- Corrige erreurs OCR AVANT extraction articles
- Prompt optimisé anti-hallucination
- Fallback vers OCR brut si AI échoue

### `OcrQualityService` (law-qa)
**Responsabilité** : Validation qualité OCR

**Métriques** :
- Confiance globale (0.0-1.0)
- Séquence articles (gaps, duplicates, ordre)
- Dictionnaire français (~336k mots)
- Mots non reconnus (enregistrement + pénalité)

### `JsonQualityService` (law-qa)
**Responsabilité** : Validation qualité JSON

**Validations** :
- Structure JSON complète
- Métadonnées obligatoires
- Cohérence articles (indices séquentiels)
- Score global (0.0-1.0)

---

## ⚙️ Configuration

### Seuils de Qualité

```yaml
law:
  quality:
    ocr-threshold: 0.3    # Seuil confiance OCR (déclenche AI correction si <)
    json-threshold: 0.5   # Seuil qualité JSON (déclenche AI correction si <)
```

### Exemples

**Document récent (bonne qualité OCR)** :
```
OCR confiance: 0.92 ≥ 0.3 ✅ → Skip AI correction OCR
JSON qualité: 0.87 ≥ 0.5 ✅ → SUCCESS
Pipeline: ÉTAPE 1 → ÉTAPE 3 → ÉTAPE 6 (SUCCESS)
```

**Document ancien (mauvaise qualité OCR)** :
```
OCR confiance: 0.18 < 0.3 ⚠️ → AI correction OCR
OCR confiance après AI: 0.52 ≥ 0.3 ✅
JSON qualité: 0.64 ≥ 0.5 ✅ → SUCCESS
Pipeline: ÉTAPE 1 → ÉTAPE 2 → ÉTAPE 3 → ÉTAPE 6 (SUCCESS)
```

**Document très corrompu** :
```
OCR confiance: 0.12 < 0.3 ⚠️ → AI correction OCR
OCR confiance après AI: 0.25 < 0.3 ⚠️
JSON qualité: 0.38 < 0.5 ⚠️ → AI correction JSON (TODO)
JSON qualité après AI: 0.42 < 0.5 ⚠️ → AI extraction complète (TODO)
JSON qualité après AI full: 0.35 < 0.5 ❌ → FAILED
Pipeline: ÉTAPE 1 → ÉTAPE 2 → ÉTAPE 3 → ÉTAPE 4 → ÉTAPE 5 → ÉTAPE 6 (FAILED)
```

---

## 📝 Logs Attendus

### Transformation Réussie (Sans AI)

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🚀 [loi-2024-15] Démarrage transformation PDF → JSON avec fallback cascade
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
▶️  1️⃣ [loi-2024-15] Extraction OCR + Corrections CSV
✅ [loi-2024-15] OCR extraction: 42 articles, confiance 0.92
🎯 [loi-2024-15] Confiance OCR brut: 0.92 (seuil: 0.3)
✅ [loi-2024-15] OCR confiance OK, skip AI correction OCR
📊 [loi-2024-15] Qualité JSON: 0.87 (seuil: 0.5)
✅ [loi-2024-15] JSON qualité OK, skip AI correction JSON
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ [loi-2024-15] Transformation réussie avec qualité JSON: 0.87
🎯 [loi-2024-15] Confiance finale: 0.92, Source: OCR:PROGRAMMATIC
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### Transformation avec AI Correction OCR

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🚀 [decret-1975-123] Démarrage transformation PDF → JSON avec fallback cascade
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
▶️  1️⃣ [decret-1975-123] Extraction OCR + Corrections CSV
✅ [decret-1975-123] OCR extraction: 12 articles, confiance 0.18
🎯 [decret-1975-123] Confiance OCR brut: 0.18 (seuil: 0.3)
⚠️ [decret-1975-123] Confiance OCR < seuil → Tentative AI correction OCR
▶️  2️⃣ [decret-1975-123] AI Correction OCR
✅ [decret-1975-123] AI correction OCR: 14 articles, confiance 0.52
✅ [decret-1975-123] AI correction OCR améliore confiance: 0.18 → 0.52
📊 [decret-1975-123] Qualité JSON: 0.64 (seuil: 0.5)
✅ [decret-1975-123] JSON qualité OK, skip AI correction JSON
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ [decret-1975-123] Transformation réussie avec qualité JSON: 0.64
🎯 [decret-1975-123] Confiance finale: 0.52, Source: OLLAMA:gemma3n
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### Transformation Échouée (Qualité Insuffisante)

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🚀 [decret-1968-corrupted] Démarrage transformation PDF → JSON avec fallback cascade
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
▶️  1️⃣ [decret-1968-corrupted] Extraction OCR + Corrections CSV
✅ [decret-1968-corrupted] OCR extraction: 3 articles, confiance 0.12
🎯 [decret-1968-corrupted] Confiance OCR brut: 0.12 (seuil: 0.3)
⚠️ [decret-1968-corrupted] Confiance OCR < seuil → Tentative AI correction OCR
▶️  2️⃣ [decret-1968-corrupted] AI Correction OCR
✅ [decret-1968-corrupted] AI correction OCR: 4 articles, confiance 0.25
⏭️ [decret-1968-corrupted] AI correction OCR n'améliore pas, garder OCR brut
📊 [decret-1968-corrupted] Qualité JSON: 0.38 (seuil: 0.5)
⚠️ [decret-1968-corrupted] Qualité JSON < seuil → Tentative AI correction JSON
▶️  3️⃣ [decret-1968-corrupted] AI Correction JSON
⚠️ [decret-1968-corrupted] AI correction JSON non implémentée, skip
⚠️ [decret-1968-corrupted] Qualité JSON toujours < seuil → Fallback AI extraction complète
▶️  4️⃣ [decret-1968-corrupted] AI Extraction Complète (PDF → JSON direct)
❌ [decret-1968-corrupted] AI extraction complète échouée: non implémentée
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
❌ [decret-1968-corrupted] ÉCHEC : Qualité JSON finale insuffisante: 0.38
❌ [decret-1968-corrupted] Document marqué FAILED, skip traitement
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 🧪 Tests

### Test Unitaire

```bash
mvn test -pl law-tojson/law-json-config -Dtest=LawTransformationServiceTest
```

### Test Intégration

```bash
# Document bonne qualité (skip AI)
java -jar law-app.jar --job=pdfToJsonJob --doc=loi-2024-15

# Document ancienne qualité (avec AI)
java -jar law-app.jar --job=pdfToJsonJob --doc=decret-1975-123 --force
```

---

## 📈 Métriques de Qualité

### Facteurs Confiance OCR (OcrQualityService)

| Facteur | Poids | Description |
|---------|-------|-------------|
| **Articles** | 20% | Nombre d'articles extraits (max 10) |
| **Séquence** | 20% | Qualité séquence (gaps/duplicates/ordre) |
| **Texte** | 15% | Longueur totale (min 5000 chars) |
| **Dictionnaire** | 25% | Taux mots reconnus (français) |
| **Termes Juridiques** | 20% | Présence termes légaux (8 max) |

### Score Qualité JSON (JsonQualityService)

- **Structure** : Présence champs obligatoires (documentId, type, year, number, articles)
- **Métadonnées** : Complétude _metadata (confidence, source, timestamp)
- **Articles** : Cohérence indices (séquence 1→2→3, pas gaps)
- **Signataires** : Présence et validité

---

## 🚀 Prochaines Étapes

### TODO : Étape 4 - AI Correction JSON

```java
/**
 * ÉTAPE 4 : AI Correction du JSON extrait.
 * 
 * Implémentation prévue :
 * - Prompt : "Complète les métadonnées manquantes dans ce JSON"
 * - AI lit JSON existant + PDF
 * - AI retourne JSON enrichi (titres, dates, signataires)
 * - Compare qualité avant/après
 */
private JsonResult transformWithAiJsonCorrection(LawDocument document, JsonResult currentResult) {
    // TODO: Implémenter OcrCorrectionService.correctJsonWithAI()
}
```

### TODO : Étape 5 - AI Extraction Complète

```java
/**
 * ÉTAPE 5 : AI Extraction complète (PDF direct → JSON).
 * 
 * Implémentation prévue :
 * - Prompt : "Extrait TOUTES les données de ce document PDF"
 * - AI lit PDF directement (sans OCR intermédiaire)
 * - AI retourne JSON complet structuré
 * - Dernier recours avant FAILED
 */
private JsonResult transformWithAiFull(LawDocument document, Path pdfPath) {
    // TODO: Implémenter OcrCorrectionService.extractFullJsonFromPdf()
}
```

---

## 🎯 Bénéfices

1. **Qualité maximale** : Jusqu'à 5 tentatives pour atteindre seuil
2. **Résilience** : Fallback automatique si stratégie échoue
3. **Traçabilité** : Logs détaillés à chaque étape
4. **Amélioration continue** : Validation via law-qa après chaque transformation
5. **Flexibilité** : Seuils configurables selon besoins projet

---

## 📚 Références

- **[architecture.md](../guides/architecture.md)** : Architecture globale
- **[fixjob.md](fixjob.md)** : Correction automatique qualité
- **[sequence-quality.md](sequence-quality.md)** : Pénalité séquence articles
- **[modules/json-config.md](../modules/json-config.md)** : Documentation law-json-config

---

**Date création** : 12 décembre 2025  
**Version** : 1.0-SNAPSHOT  
**Statut** : ✅ ÉTAPES 1-3 IMPLÉMENTÉES, ÉTAPES 4-5 TODO

