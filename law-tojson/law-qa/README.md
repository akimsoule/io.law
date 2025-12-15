# law-qa - Quality Assurance Module

## Description

Module dédié au contrôle de qualité des données extraites (OCR et JSON). Centralise toute la logique de validation et de scoring pour garantir la cohérence et la fiabilité des données.

## Responsabilités

1. **Validation OCR** : Calcul de confiance de l'extraction OCR
2. **Validation Structure** : Vérification des 5 parties obligatoires d'un document de loi ✨ **NOUVEAU**
3. **Validation JSON** : Validation structure et complétude des fichiers JSON
4. **Suivi qualité** : Tracking des mots non reconnus pour amélioration continue

---

## Services

### 1. OcrQualityService

#### 1.1 Calcul de Confiance

Calcul de confiance de l'extraction OCR basé sur **5 facteurs pondérés** :

| Facteur | Poids | Description |
|---------|-------|-------------|
| **Articles** | 20% | Nombre d'articles extraits (max 10) |
| **Séquence** | 20% | Qualité séquence (gaps/duplicates/out-of-order) |
| **Texte** | 15% | Longueur totale (min 5000 chars) |
| **Dictionnaire** | 25% | Taux mots reconnus (dictionnaire FR ~336k mots) |
| **Termes Juridiques** | 20% | Présence termes légaux (18 termes) |

**Formule Confiance :**

```java
confidence = (articles * 0.20) + (sequence * 0.20) + (text * 0.15) + 
             (dictionary * 0.25) + (legal * 0.20)
```

#### 1.2 Validation de Structure ✨ **NOUVEAU**

Vérifie la présence des **5 parties obligatoires** d'un document de loi :

1. **Entête** : RÉPUBLIQUE DU BENIN + Fraternité-Justice-Travail + PRÉSIDENCE
2. **Titre** : LOI N° ...
3. **Visa** : L'assemblée nationale a délibéré...
4. **Corps** : Articles jusqu'à "sera exécutée comme loi de l'État"
5. **Pied** : Fait à ... jusqu'à AMPLIATIONS

**Score :** `0.2` par section présente (max `1.0`)

📚 **Documentation complète** : [VALIDATION_STRUCTURE.md](VALIDATION_STRUCTURE.md)

```java
double structureScore = ocrQualityService.validateDocumentStructure(ocrText);
// structureScore = 5/5 sections → 1.0 ✅
// structureScore = 4/5 sections → 0.8 ⚠️
// structureScore = 0/5 sections → 0.0 ❌
```

#### 1.3 Validation Séquence

Détecte 3 types d'anomalies :

- **Gaps** : Articles manquants (ex: 1→3→5)
  - Pénalité : **15% par article manquant**

- **Duplicates** : Index répétés (ex: 1→2→2→3)
  - Pénalité : **25% par duplicate**

- **Out-of-Order** : Séquence inversée (ex: 3→2→1)
  - Pénalité : **30% par inversion**

```java
sequenceScore = max(0.0, 1.0 - totalPenalty)
```

#### Termes Juridiques (18)

```java
article, loi, décret, portant, promulgué, république, 
assemblée, nationale, président, ministre, dispositions, 
abroge, modifie, chapitre, section, ordonnance, arrêté, 
délibération, constitution
```

#### Usage

```java
@Autowired
private OcrQualityService ocrQualityService;

public void validateOcr(String ocrText, List<Article> articles, String documentId) {
    // Avec tracking mots non reconnus
    double confidence = ocrQualityService.calculateConfidence(ocrText, articles, documentId);
    
    // Sans tracking
    double confidence = ocrQualityService.calculateConfidence(ocrText, articles);
    
    // Validation séquence seule
    double sequenceScore = ocrQualityService.validateSequence(articles);
    
    // Validation dictionnaire seule
    double dictScore = ocrQualityService.validateDictionary(ocrText);
}
```

---

### 2. JsonQualityService

Validation qualité des fichiers JSON extraits avec **4 dimensions** :

| Dimension | Poids | Critère |
|-----------|-------|---------|
| **Structure** | 30% | JSON valide, sections obligatoires présentes |
| **Metadata** | 30% | Complétude métadonnées (10 champs) |
| **Articles** | 30% | Séquence cohérente, indices valides |
| **Signataires** | 10% | Présence signataires (optionnel) |

#### Validation Metadata

Score sur **10 points** :

**Champs obligatoires (6)** :
- documentId
- documentType
- documentYear
- documentNumber
- title
- totalArticles

**Champs optionnels (4)** :
- publicationDate
- extractionMethod
- confidence
- extractionDate

```java
metadataScore = score / 10.0  // 0.0 à 1.0
```

#### Usage

```java
@Autowired
private JsonQualityService jsonQualityService;

public void validateJson(String jsonContent, DocumentMetadata metadata) {
    // Validation structure
    boolean isValid = jsonQualityService.validateStructure(jsonContent);
    
    // Score metadata
    double metadataScore = jsonQualityService.validateMetadata(metadata);
    
    // Validation indices articles
    List<Integer> indices = Arrays.asList(1, 2, 3, 4);
    boolean isSequential = jsonQualityService.validateArticleIndices(indices);
    
    // Score qualité global
    double quality = jsonQualityService.calculateJsonQualityScore(jsonContent);
}
```

---

### 3. UnrecognizedWordsService

Tracking des mots non reconnus pour **amélioration continue** :

#### Fonctionnalités

1. **Persistence** : Sauvegarde dans `data/word_non_recognize.txt`
2. **Déduplication** : Un mot = une ligne (unicité garantie)
3. **Pénalité Progressive** : Calcul basé sur taux et volume
4. **Thread-safe** : `ConcurrentHashMap` pour accès concurrent

#### Algorithme Pénalité

```java
// Tiers progressifs basés sur le taux
if (rate < 0.10)      penalty = rate * 2.0;              // 0-10% → 0.0-0.2
else if (rate < 0.30) penalty = 0.2 + (rate-0.10) * 1.5; // 10-30% → 0.2-0.5
else if (rate < 0.50) penalty = 0.5 + (rate-0.30) * 1.5; // 30-50% → 0.5-0.8
else                  penalty = 0.8 + (rate-0.50) * 0.4; // >50% → 0.8-1.0

// Ajustement volume absolu
if (totalUnrecognized > 100) penalty += 0.05;
if (totalUnrecognized > 200) penalty += 0.05;

penalty = Math.min(1.0, penalty);  // Cap à 1.0
```

#### Usage

```java
@Autowired
private UnrecognizedWordsService unrecognizedWordsService;

public void trackWords(Set<String> words, String documentId) {
    // Enregistrer mots non reconnus
    unrecognizedWordsService.recordUnrecognizedWords(words, documentId);
    
    // Calculer pénalité
    double penalty = unrecognizedWordsService.calculateUnrecognizedPenalty(0.25, 150);
    
    // Compter total connu
    int totalKnown = unrecognizedWordsService.getTotalUnrecognizedWordsCount();
    
    // Charger mots existants
    Set<String> existing = unrecognizedWordsService.loadExistingWords();
}
```

---

## Configuration

### Dictionnaire Français

**Emplacement** : `src/main/resources/dictionaries/french-wordlist.txt`

**Contenu** : ~336 000 mots français (noms communs, adjectifs, verbes conjugués)

**Chargement** : Au démarrage du service via classpath

```java
InputStream is = getClass().getResourceAsStream("/dictionaries/french-wordlist.txt");
```

### Fichier Mots Non Reconnus

**Emplacement** : `data/word_non_recognize.txt`

**Format** : Un mot par ligne (unicité)

**Création** : Automatique si inexistant

```
béninoise
rjuillet
com
apatridie
narticle
...
```

---

## Dépendances

```xml
<dependencies>
    <!-- Modules io.law -->
    <dependency>
        <groupId>bj.gouv.sgg</groupId>
        <artifactId>law-common</artifactId>
    </dependency>
    <dependency>
        <groupId>bj.gouv.sgg</groupId>
        <artifactId>law-json-config</artifactId>
    </dependency>
    
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    
    <!-- JSON (Gson) -->
    <dependency>
        <groupId>com.google.code.gson</groupId>
        <artifactId>gson</artifactId>
    </dependency>
    
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
</dependencies>
```

---

## Intégration

### Dans law-ocr-json

```xml
<dependency>
    <groupId>bj.gouv.sgg</groupId>
    <artifactId>law-qa</artifactId>
</dependency>
```

```java
@Service
@RequiredArgsConstructor
public class ArticleRegexExtractor {
    
    private final OcrQualityService ocrQualityService;
    
    public double calculateConfidence(String ocrText, List<Article> articles, String documentId) {
        // Déléguer au service QA
        return ocrQualityService.calculateConfidence(ocrText, articles, documentId);
    }
}
```

---

## Tests

### Tests Unitaires

```bash
# Tous les tests du module
mvn test -pl law-tojson/law-qa

# Test spécifique
mvn test -pl law-tojson/law-qa -Dtest=OcrQualityServiceImplTest
```

### Tests d'Intégration

```bash
# Avec base de données
mvn verify -pl law-tojson/law-qa
```

---

## Exemples

### Extraction avec Confiance

```java
String ocrText = "Article 1er. La présente loi...";
List<Article> articles = extractArticles(ocrText);
String documentId = "loi-2024-15";

double confidence = ocrQualityService.calculateConfidence(ocrText, articles, documentId);

if (confidence < 0.3) {
    log.warn("❌ Low confidence: {} for {}", confidence, documentId);
    // Déclencher re-extraction IA
}
```

### Validation JSON

```java
String jsonContent = Files.readString(jsonPath);

double quality = jsonQualityService.calculateJsonQualityScore(jsonContent);

if (quality < 0.5) {
    log.error("❌ Poor JSON quality: {} for {}", quality, documentId);
    // Ne pas consolider en BD
}
```

### Analyse Mots Non Reconnus

```bash
# Vérifier fichier
wc -l data/word_non_recognize.txt
# 60 data/word_non_recognize.txt

# Top 20 mots
tail -20 data/word_non_recognize.txt
```

---

## Logs

### OcrQualityService

```
✅ [loi-2024-15] Confidence: 0.92 (articles=1.0, seq=1.0, text=0.95, dict=0.88, legal=1.0)
⚠️ [decret-1960-12] Sequence issues: 2 gaps detected → score=0.70
📊 [loi-2025-6] Dictionary: 150/200 words recognized (75%) → penalty=0.25
```

### JsonQualityService

```
✅ [loi-2024-15] JSON quality: 0.85 (structure=1.0, metadata=0.9, articles=1.0, sig=1.0)
⚠️ [decret-2024-1] JSON structure invalid: metadata=true, articles=false
❌ [loi-1960-5] Metadata incomplete: score=0.3 (3/10 fields)
```

### UnrecognizedWordsService

```
📝 [loi-2024-15] Recorded 7 new unrecognized words (total: 67)
📊 [decret-2024-1632] Top unrecognized: béninoise=11, com=3, narticle=2
💾 [loi-2025-6] Persisted 15 words to data/word_non_recognize.txt
```

---

## Métriques Qualité

### Confiance OCR

- **Excellente** : ≥ 0.85
- **Bonne** : 0.70 - 0.84
- **Moyenne** : 0.50 - 0.69
- **Faible** : 0.30 - 0.49
- **Très faible** : < 0.30 → Re-extraction recommandée

### Qualité JSON

- **Excellente** : ≥ 0.80
- **Bonne** : 0.60 - 0.79
- **Acceptable** : 0.40 - 0.59
- **Insuffisante** : < 0.40 → Ne pas consolider

---

## Évolutions Futures

- [ ] **Configurable** : Seuils et poids dans `application.yml`
- [ ] **Métriques** : Exposition Prometheus pour monitoring
- [ ] **Alertes** : Notifications si qualité dégradée
- [ ] **ML** : Apprentissage automatique sur patterns OCR
- [ ] **API REST** : Endpoints pour audit qualité externe

---

**Date création** : 10 décembre 2025  
**Version** : 1.0-SNAPSHOT  
**Package** : bj.gouv.sgg.qa.service
