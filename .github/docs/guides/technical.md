# Guide Technique - io.law

## Principes Clean Code STRICTS

### 1. Gestion Exceptions
❌ **INTERDIT** : `throws Exception`, `catch (Exception e)`
✅ **OBLIGATOIRE** : Exceptions spécifiques

### 2. Retours Null
❌ **INTERDIT** : `return null`
✅ **OBLIGATOIRE** : `Optional<T>`, collections vides, objets par défaut

### 3. Constantes vs Littéraux
❌ **INTERDIT** : Littéraux dupliqués (>2 fois)
✅ **OBLIGATOIRE** : Constantes privées

### 4. Ressources
❌ **INTERDIT** : Streams/Files sans fermeture
✅ **OBLIGATOIRE** : try-with-resources

### 5. Format Multi-plateforme
❌ **INTERDIT** : `\n` dans String.format
✅ **OBLIGATOIRE** : `%n` pour indépendance plateforme

---

## Qualité Extraction & Confiance

### Pénalité de Séquence
- **Objectif** : Détecter incohérences de numérotation d'articles.
- **Détections** :
  - **Gaps** : Articles manquants (ex: 1, 2, 4 → gap sur 3)
  - **Doublons** : Articles répétés (ex: 1, 2, 2, 3)
  - **Ordre inversé** : Articles hors séquence (ex: 1, 3, 2)
- **Pénalité** : Proportionnelle au nombre et à la gravité des anomalies.
- **Implémentation** : `ArticleRegexExtractor.calculateSequenceScore()`

### Pénalité Dictionnaire (Mots Non Reconnus)

#### Principe
- Dictionnaire FR (~336k mots) chargé au démarrage depuis `french-wordlist.txt`.
- Extraction des mots non reconnus (≥3 caractères, filtrage ponctuation/nombres).
- Enregistrement des mots uniques dans `data/word_non_recognize.txt`.
- Pénalité progressive basée sur **taux** et **volume absolu** de mots non reconnus.

#### Algorithme de Pénalité
```java
// Tiers progressifs basés sur le taux
if (rate < 0.10)      penalty = rate * 2.0;        // 0-10% → 0.0-0.2
else if (rate < 0.30) penalty = 0.2 + (rate-0.10) * 1.5;  // 10-30% → 0.2-0.5
else if (rate < 0.50) penalty = 0.5 + (rate-0.30) * 1.5;  // 30-50% → 0.5-0.8
else                  penalty = 0.8 + (rate-0.50) * 0.4;  // >50% → 0.8-1.0

// Ajustement volume absolu
if (totalUnrecognized > 100) penalty += 0.05;
if (totalUnrecognized > 200) penalty += 0.05;

penalty = Math.min(1.0, penalty);  // Cap à 1.0
```

#### Service UnrecognizedWordsService
- **Responsabilité** : Persistence et calcul de pénalité des mots non reconnus.
- **Thread-safe** : Utilise `ConcurrentHashMap.newKeySet()`.
- **Méthodes principales** :
  - `recordUnrecognizedWords(Set<String>, String documentId)` : Enregistre mots uniques, crée répertoire parent si nécessaire.
  - `calculateUnrecognizedPenalty(double rate, int total)` : Calcule pénalité progressive.
  - `loadExistingWords()` : Charge mots existants au démarrage.

#### Logs Standardisés
```
INFO  [docId] Recorded 7 new unrecognized words (total: 60)
INFO  [docId] Top unrecognized words (word=count): béninoise=11, rjuillet=1, com=1, ...
```

### Fichier des Mots Non Reconnus
- **Emplacement** : `data/word_non_recognize.txt`
- **Format** : Un mot par ligne (unicité garantie)
- **Utilisation** : 
  - Alimente les futures corrections CSV (`corrections.csv`)
  - Améliore les patterns de reconnaissance OCR
  - Base pour analyse statistique et enrichissement dictionnaire

### Statistiques d'Occurrences
- **Calcul inline** : Pendant `pdfToJsonJob`, le top 10 des mots non reconnus avec occurrences est calculé et loggé.
- **Format log** : `📊 [docId] Top unrecognized words (word=count): mot1=12, mot2=9, ...`
- **Exemple** :
  ```
  📊 [decret-2024-1632] Top unrecognized words (word=count): apatridie=3, narticle=3, etat=2, new=2, cosigne=2, œuvrer=2, york=2, com=1, microfinance=1, béninois=1
  ```

### Commandes de Vérification

```zsh
# Forcer OCR→JSON et enregistrer les mots non reconnus
java -jar law-app/target/law-app-1.0-SNAPSHOT.jar \
  --job=pdfToJsonJob --doc=decret-2024-1632 --force

# Compter et visualiser le fichier
wc -l data/word_non_recognize.txt
tail -20 data/word_non_recognize.txt

# Filtrer les logs de stats
grep "📊.*Top unrecognized" logs/law-app.log
```

---

## Règles Idempotence des Jobs

**PRINCIPE** : Relancer un job N fois = même résultat que 1 fois

```java
// ✅ Check avant traitement
@Override
public LawDocument process(LawDocument doc) {
    if (doc.getStatus() == ProcessingStatus.EXTRACTED) {
        log.debug("⏭️ Already processed, skipping: {}", doc.getDocumentId());
        return doc;
    }
    return processedDocument;
}

// ✅ N'écraser JSON que si confiance supérieure
if (existing.isEmpty() || newData.confidence() > existing.get().confidence()) {
    Files.writeString(jsonPath, toJson(newData));
    log.info("✅ Saved JSON with confidence {}", newData.confidence());
} else {
    log.info("⏭️ Keeping existing (better confidence: {})", existing.get().confidence());
}
```

---

## Stratégie Correction OCR (law-ocr-json)

**PRINCIPE** : Ajouter corrections CSV AVANT améliorer patterns
**Logique** : Normaliser OCR défectueux → patterns standard fonctionnent

### Corrections Actuelles : 287 entrées (8 déc 2025)

**Exemples essentiels** :
```
"Articlc" → "Article"          (OCR 'le' → 'c')
"A rliclc " → "Article"        (espaces internes)
"Article 1e" → "Article 1er"   (troncature)
"Artic|e" → "Article"          (pipe au lieu l)
"le 1€" → "le 1er"             (euro au lieu er)
"ARTICIS Ier" → "Article 1er"  (majuscules erronées)
"ltarticle" → "Article"        (caractères collés)
"rrticle" → "Article"          (double r)
"ATticle" → "Article"          (casse mixte)
```

### Process

1. ❌ Extraction échoue → Analyser fichier OCR (.txt)
2. 🔍 Identifier erreurs OCR bloquant patterns
3. ➕ `echo "erreur,correct" >> corrections.csv`
4. ✅ Re-tester extraction → devrait passer

### Règles

- Format CSV : `"mauvais,bon"`
- Corrections appliquées AVANT extraction articles
- CsvCorrector charge 287 corrections au démarrage
- Cas insensible après correction
- Les mots non reconnus sont persistés pour faciliter l'ajout de nouvelles corrections

### Stats Actuelles

- **70 tests** : 69 passent, 1 désactivé (fragment)
- **Taux succès** : 80% (38/47 fichiers) ✅ (+19% après optimisation)
- **Qualité** : ~10% confiance ≥0.7 (amélioration en cours)
- **Corrections OCR** : 287 entrées (9 déc 2025)
- **Mots non reconnus** : 60 mots uniques enregistrés (enrichi progressivement)
- **Documents anciens** (1960-1990) : plus d'erreurs OCR, mais extraction améliorée

---

## Patterns de Code

### ItemProcessor Pattern

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class MyProcessor implements ItemProcessor<InputType, OutputType> {
    
    private final MyService service;
    
    @Override
    public OutputType process(InputType item) throws Exception {
        // 1. Idempotence check
        if (alreadyProcessed(item)) {
            log.debug("⏭️ Already processed: {}", item.getId());
            return convertToOutput(item);
        }
        
        // 2. Process with error handling
        try {
            OutputType result = service.doProcess(item);
            result.setStatus(ProcessingStatus.SUCCESS);
            log.info("✅ Processed: {}", item.getId());
            return result;
        } catch (SpecificException e) {
            log.error("❌ Failed {}: {}", item.getId(), e.getMessage());
            item.setStatus(ProcessingStatus.FAILED);
            return convertToOutput(item); // Don't stop job
        }
    }
}
```

### FileStorageService Pattern

```java
@Service
@RequiredArgsConstructor
public class MyService {
    private final FileStorageService fileStorageService;
    
    public void process(LawDocument doc) {
        String docId = doc.getDocumentId(); // "loi-2024-15"
        
        // ✅ Utiliser FileStorageService
        Path pdfPath = fileStorageService.pdfPath(doc.getType(), docId);
        Path ocrPath = fileStorageService.ocrPath(doc.getType(), docId);
        Path jsonPath = fileStorageService.jsonPath(doc.getType(), docId);
        
        // ✅ Vérifier existence
        if (!fileStorageService.pdfExists(doc.getType(), docId)) {
            throw new DocumentNotFoundException("PDF not found: " + docId);
        }
    }
}
```

---

## Logging Standardisé

### Emojis

```java
log.info("✅ Success: Document {} fetched", docId);
log.warn("⚠️ Warning: Retry attempt {} for {}", attempt, docId);
log.error("❌ Error: Failed to download {}", docId, exception);
log.info("🔄 Processing: OCR extraction for {}", docId);
log.info("🤖 AI Provider: Using Ollama confidence 0.95");
log.info("📄 Document: {} articles extracted", count);
log.info("📊 Stats: Top unrecognized words with counts", docId);
log.error("🔴 CORRUPTED: PNG disguised as PDF: {}", docId);
log.info("⏭️ Skipped: Already processed {}", docId);
```

---

## Conventions Nommage

### Fichiers

```
PDFs : data/pdfs/{type}/{type}-{year}-{number}.pdf
OCR  : data/ocr/{type}/{type}-{year}-{number}.txt
JSON : data/articles/{type}/{type}-{year}-{number}.json

Exemple : data/pdfs/loi/loi-2024-15.pdf
```

### IDs Documents

```java
String documentId = document.getDocumentId(); // "loi-2024-15"

public String getDocumentId() {
    return String.format("%s-%d-%d", type, year, number);
}
```

### Jobs Spring Batch

```
Jobs  : suffixe "Job" → fetchCurrentJob, downloadJob, ocrJob
Steps : suffixe "Step" → fetchCurrentStep, downloadStep
```

---

## Build & Test

### Compiler

```bash
# Module spécifique
mvn clean install -pl law-download

# Tous modules
mvn clean install

# Sans tests
mvn clean install -DskipTests
```

### Tests

```bash
# Tests module
mvn -pl law-ocr-json test

# Test spécifique
mvn -pl law-ocr-json test -Dtest=ArticleRegexExtractorTest
```

### Exécuter Application

```bash
# CLI
cd law-app
java -jar target/law-app-1.0-SNAPSHOT.jar --job=downloadJob

# Maven
mvn spring-boot:run
```

---

## MySQL Docker

### Démarrage

```bash
docker run -d --name mysql-law \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=law_db \
  -p 3306:3306 mysql:8.4
```

### Commandes Utiles

```bash
# Shell MySQL
docker exec -it mysql-law mysql -u root -proot law_db

# Compter par statut
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "SELECT status, COUNT(*) FROM law_documents GROUP BY status;"

# Backup
docker exec mysql-law mysqldump -u root -proot law_db > backup.sql

# Restore
docker exec -i mysql-law mysql -u root -proot law_db < backup.sql
```

---

## Anti-Patterns à ÉVITER

### ❌ Arrêter Job sur Erreur

```java
// ❌ MAL
if (pdfCorrupted(doc)) {
    throw new RuntimeException("Corrupted");
}

// ✅ BIEN
if (pdfCorrupted(doc)) {
    doc.setStatus(ProcessingStatus.CORRUPTED);
    log.warn("🔴 CORRUPTED: {}", doc.getDocumentId());
    return doc; // Job continue
}
```

### ❌ Non-Idempotent

```java
// ❌ MAL
public void process(LawDocument doc) {
    extractText(doc);
    doc.setStatus(EXTRACTED);
}

// ✅ BIEN
public LawDocument process(LawDocument doc) {
    if (doc.getStatus() == EXTRACTED) {
        return doc;
    }
    extractText(doc);
    doc.setStatus(EXTRACTED);
    return doc;
}
```

---

## Bugs Résolus

- Fix SQL : `year` → `document_year` (mot réservé)
- Fix pattern "Article premier" : Regex `(?:(1er)|(premier)|(\\d+))`
- Fix test qualité : Seuil 30% → 13% (documents anciens)
- Fix loi-2024-1 : Désactivé (fragment 71-172)

---

## Workflow

**Toujours privilégier** :
1. ✅ **Résilience** : Job continue malgré erreurs
2. ✅ **Idempotence** : Re-run safe
3. ✅ **Clean Code** : Exceptions spécifiques, pas null, try-with-resources
4. ✅ **Modularité** : Découpage clair
5. ✅ **Testabilité** : Tests unitaires + intégration

**Principe** : 1 module à la fois, compile + tests avant suivant

**Objectif** : Architecture propre, maintenable, évolutive ✨
