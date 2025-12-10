# Pénalité Confiance Basée sur la Séquence d'Articles

## Vue d'Ensemble

Depuis le **9 décembre 2025**, le calcul de confiance de l'extraction OCR inclut une nouvelle métrique : **la qualité de séquence des articles**.

Cette amélioration permet de détecter automatiquement les extractions de mauvaise qualité où les articles ne suivent pas une numérotation séquentielle normale (1→2→3→...).

---

## Problème Résolu

**Avant** : Un document avec articles `1, 3, 5, 7` (plusieurs articles manquants) pouvait avoir une confiance élevée (0.99) car d'autres facteurs (longueur texte, termes juridiques) étaient corrects.

**Maintenant** : Le même document aura sa confiance réduite proportionnellement aux problèmes détectés dans la séquence, signalant ainsi une extraction incomplète.

---

## Algorithme de Détection

### Types de Problèmes Détectés

1. **Gaps (Trous)** : Articles manquants dans la séquence
   - Exemple : `1→3→5` (articles 2 et 4 manquants)
   - Pénalité : **15% par article manquant**

2. **Duplicates** : Même index répété
   - Exemple : `1→2→2→3` (article 2 en double)
   - Pénalité : **25% par duplicate**

3. **Out-of-Order** : Séquence décroissante
   - Exemple : `3→2→1` (ordre inversé)
   - Pénalité : **30% par inversion**

### Formule

```java
sequenceScore = 1.0 - (gaps * 0.15) - (duplicates * 0.25) - (outOfOrder * 0.30)
sequenceScore = max(0.0, sequenceScore)  // Minimum 0.0
```

---

## Calcul de Confiance Global

Le score de confiance final utilise **5 facteurs pondérés** :

| Facteur | Poids | Description |
|---------|-------|-------------|
| **Articles** | 20% | Nombre d'articles extraits (max 10) |
| **Séquence** ✨ | 20% | Qualité séquence (nouveau) |
| **Texte** | 15% | Longueur totale (min 5000 chars) |
| **Dictionnaire** | 25% | Taux mots reconnus (français) |
| **Termes Juridiques** | 20% | Présence termes légaux (8 max) |

```java
confidence = (articles * 0.20) + (sequence * 0.20) + (text * 0.15) + 
             (dictionary * 0.25) + (legal * 0.20)
```

---

## Exemples Concrets

### 1. Séquence Parfaite ✅
**Articles** : `1→2→3→4→5`  
**Résultat** : `sequence=1.0` (aucune pénalité)

```
Confidence calculation: articles=0.5, sequence=1.0, text=0.8, dict=0.95, legal=0.9 → total=0.83
```

---

### 2. Articles Manquants (Gaps) ⚠️
**Articles** : `1→3→5` (2 et 4 manquants)  
**Pénalité** : `2 gaps × 15% = 30%`  
**Résultat** : `sequence=0.70`

```
Sequence quality: 3 articles, 2 gaps, 0 duplicates, 0 out-of-order → score=0.70
Confidence calculation: articles=0.3, sequence=0.70, text=0.6, dict=0.90, legal=0.8 → total=0.68
```

---

### 3. Duplicates ⚠️
**Articles** : `1→2→2→3` (article 2 en double)  
**Pénalité** : `1 duplicate × 25% = 25%`  
**Résultat** : `sequence=0.75`

```
Sequence quality: 4 articles, 0 gaps, 1 duplicates, 0 out-of-order → score=0.75
```

---

### 4. Séquence Inversée ❌
**Articles** : `3→2→1` (ordre décroissant)  
**Pénalité** : `2 inversions × 30% = 60%`  
**Résultat** : `sequence=0.40`

```
Sequence quality: 3 articles, 0 gaps, 0 duplicates, 2 out-of-order → score=0.40
Confidence calculation: articles=0.3, sequence=0.40, text=0.7, dict=0.85, legal=0.8 → total=0.61
```

---

### 5. Problèmes Multiples ❌
**Articles** : `1→3→3→2` (gap + duplicate + inversion)  
**Pénalité** : `(1 × 15%) + (1 × 25%) + (1 × 30%) = 70%`  
**Résultat** : `sequence=0.30`

```
Sequence quality: 4 articles, 1 gaps, 1 duplicates, 1 out-of-order → score=0.30
```

---

### 6. Gap Énorme ❌
**Articles** : `1→10` (articles 2-9 manquants)  
**Pénalité** : `8 gaps × 15% = 120%` → Limité à 0.0  
**Résultat** : `sequence=0.0`

```
Sequence quality: 2 articles, 8 gaps, 0 duplicates, 0 out-of-order → score=0.0
Confidence calculation: articles=0.2, sequence=0.0, text=0.4, dict=0.75, legal=0.6 → total=0.39
```

---

## Cas Particuliers

### Article Unique
- **Index 1** : `sequence=1.0` (normal)
- **Index ≠ 1** : `sequence=0.8` (pénalité légère car ne commence pas à 1)

### Liste Vide
- `sequence=0.0` (aucun article extrait)

---

## Impact sur Documents Existants

### Documents Bien Extraits (Séquence Parfaite)
- **Avant** : `confidence=0.9904` (decret-2024-1632)
- **Après** : `confidence=0.9920` ✅ *Légère amélioration*

Les documents corrects bénéficient légèrement de la redistribution des poids.

### Documents avec Gaps
- **Avant** : `confidence=0.85` (fictif)
- **Après** : `confidence=0.70` ⚠️ *Réduction significative*

Les extractions incomplètes sont maintenant correctement pénalisées.

---

## Tests Unitaires

**8 nouveaux tests** dans `SequenceScoreTest.java` :

| Test | Scénario | Score Attendu |
|------|----------|---------------|
| `testPerfectSequence` | 1→2→3→4 | 1.0 |
| `testSequenceWithGaps` | 1→3→5 (2 gaps) | 0.70 |
| `testSequenceWithDuplicates` | 1→2→2→3 | 0.75 |
| `testSequenceOutOfOrder` | 3→2→1 | 0.40 |
| `testSequenceWithMultipleIssues` | 1→3→3→2 | 0.30 |
| `testSequenceWithHugeGap` | 1→10 | 0.0 |
| `testSingleArticleStartingAt1` | [1] | 1.0 |
| `testSingleArticleNotStartingAt1` | [5] | 0.8 |

**Résultat** : **78 tests** passent (69 existants + 8 nouveaux + 1 skip)

---

## Logging

### Format Debug
```
Sequence quality: {nb_articles} articles, {gaps} gaps, {duplicates} duplicates, {out-of-order} out-of-order → score={score}
Confidence calculation: articles={a}, sequence={s}, text={t}, dict={d}, legal={l} → total={total}
```

### Exemples Réels
```
✅ decret-2024-1632 (séquence parfaite) :
   Sequence quality: 41 articles, 0 gaps, 0 duplicates, 0 out-of-order → score=1.0
   Confidence: articles=1.0, sequence=1.0, text=1.0, dict=0.968, legal=1.0 → total=0.9920

⚠️ loi-fictive-gaps :
   Sequence quality: 5 articles, 3 gaps, 0 duplicates, 0 out-of-order → score=0.55
   Confidence: articles=0.5, sequence=0.55, text=0.7, dict=0.85, legal=0.8 → total=0.69
```

---

## Utilisation

### fullJob avec --force
```bash
java -jar law-app.jar --job=fullJob --doc=loi-2025-6 --force
```

Logs afficheront :
```
Extracted 7 articles via regex
Sequence quality: 7 articles, 0 gaps, 0 duplicates, 0 out-of-order → score=1.0
Confidence calculation: articles=0.7, sequence=1.0, text=1.0, dict=0.98, legal=1.0 → total=0.935
```

### pdfToJsonJob
```bash
java -jar law-app.jar --job=pdfToJsonJob --doc=decret-2024-1632 --force
```

---

## Bénéfices

1. **Détection automatique** : Identifie extractions incomplètes
2. **Alertes proactives** : Confiance réduite signale problèmes
3. **Priorisation** : Tri documents par confiance pour correction manuelle
4. **Traçabilité** : Logs détaillés expliquent pénalités appliquées
5. **Testabilité** : 8 tests unitaires valident comportement

---

## Évolutions Futures

### Potentielles Améliorations
- Détecter sauts logiques (Article 1 → Article 50)
- Analyser cohérence articles/chapitres
- Vérifier références croisées (Article 3 mentionne Article 2 manquant)
- Adapter pénalités selon type document (décret vs loi)

### Seuils Configurables
Envisager paramétrage :
```yaml
law:
  extraction:
    sequence:
      gap-penalty: 0.15     # 15% par article manquant
      duplicate-penalty: 0.25  # 25% par duplicate
      out-of-order-penalty: 0.30  # 30% par inversion
```

---

## Références

- **Code** : `ArticleRegexExtractor.java` (méthode `calculateSequenceScore`)
- **Tests** : `SequenceScoreTest.java` (8 tests)
- **Date** : 9 décembre 2025
- **Version** : 1.0-SNAPSHOT
