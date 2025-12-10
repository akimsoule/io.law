# GitHub Copilot Instructions - io.law

Application Spring Batch pour extraire, traiter et consolider les lois/décrets depuis https://sgg.gouv.bj/doc.

## 📚 Documentation Complète

> **[Index de la documentation](docs/INDEX.md)** : Vue d'ensemble de tous les documents

### Documents Principaux

- **[Architecture](docs/guides/architecture.md)** : Structure multi-modules, flux de données, état du projet
- **[Technique](docs/guides/technical.md)** : Clean code, patterns, OCR, qualité extraction, build & test
- **[Fonctionnel](docs/guides/functional.md)** : Configuration, jobs, pipeline, API REST, SQL

### Features & Modules

- **[fullJob](docs/features/fulljob.md)** : Pipeline complet automatique
- **[Qualité Séquence](docs/features/sequence-quality.md)** : Pénalité confiance extraction
- **[Modules](docs/modules/)** : Documentation spécifique (consolidate, json-config)

---

## Résumé Essentiel

### Technologies
- **Java 17+**, **Spring Boot 3.2.0** + Spring Batch
- **Maven Multi-Modules** (11 modules)
- **PDFBox** (extraction PDF), **Tesseract OCR** (JavaCPP)
- **MySQL 8.4** (Docker), **Ollama/Groq** (parsing IA optionnel)
 - **Suivi qualité OCR**: dictionnaire FR, pénalités de séquence et mots non reconnus

### Structure Modules

```
io.law/
├── law-common/          # Socle (models, repos, exceptions, config)
├── law-fetch/           # Récupération métadonnées (2 jobs)
├── law-download/        # Téléchargement PDFs
├── law-tojson/          # PDF → JSON (4 sous-modules)
│   ├── law-ai-pdf-json/    # Extraction IA
│   ├── law-pdf-ocr/        # Extraction OCR
│   ├── law-ocr-json/       # Parsing OCR → JSON ✅
│   ├── law-json-config/    # Config commune ✅
│   └── (law-tojson-app)/   # Orchestration (⏳ TODO)
├── law-consolidate/     # Consolidation BD ✅
└── law-app/             # API REST + CLI + orchestration
```

---

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

### Statuts Documents

```java
PENDING → FETCHED → DOWNLOADED → EXTRACTED → CONSOLIDATED
FAILED / CORRUPTED  // Statuts d'erreur
```

---

## Stratégie Correction OCR (law-ocr-json)

**PRINCIPE** :
- Ajouter corrections CSV AVANT améliorer patterns
- Mesurer la qualité via séquence d'articles et dictionnaire
- Suivre les mots OCR non reconnus pour les futures corrections

### Process

1. ❌ Extraction échoue → Analyser fichier OCR (.txt)
2. 🔍 Identifier erreurs OCR bloquant patterns
3. ➕ `echo "erreur,correct" >> corrections.csv`
4. ✅ Re-tester extraction → devrait passer
5. 🧾 Enregistrer les mots non reconnus dans `data/word_non_recognize.txt`

### Stats Actuelles

- **70 tests** : 69 passent, 1 désactivé (fragment)
- **Taux succès** : 80% (38/47 fichiers) ✅
- **Qualité** : ~10% confiance ≥0.7 (amélioration en cours)
- **Corrections OCR** : 287 entrées (8 déc 2025)
- **Mots non reconnus** : fichier initial créé (53 mots uniques) via `pdfToJsonJob --force`
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
    max-threads: 10
    max-documents-to-extract: 50
    job-timeout-minutes: 55
  
  capacity:
    ia: 4   # Score RAM/CPU IA (16GB+)
    ocr: 2  # Score OCR (4GB+)
    ollama-url: http://localhost:11434
    ollama-models-required: qwen2.5:7b
  
  groq:
    api-key: ${GROQ_API_KEY:}

quality:
    sequence-penalty: enabled   # Pénalité si numérotation des articles non séquentielle
    dictionary-penalty: enabled # Pénalité progressive via mots non reconnus
    unrecognized-words-file: data/word_non_recognize.txt

logging:
  level:
    bj.gouv.sgg: DEBUG
```

---

## État Actuel (9 décembre 2025)

### ✅ Modules Complétés

1. **law-common** : Services FileStorageService + DocumentStatusManager
2. **law-fetch** : 2 jobs (current + previous) - 66 tests unitaires + 7 intégration
3. **law-download** : 1 job - 26 tests (8 intégration + 18 unitaires)
4. **law-ocr-json** : Extraction OCR → JSON - 70 tests (69 passent, 1 désactivé)
5. **law-consolidate** : Import JSON → MySQL - Job consolidateJob opérationnel ✅

### 📊 Statistiques

- **Tests** : 162 unitaires + 15 intégration
- **Taux extraction** : 80% (38/47 fichiers) ✅
- **Taux consolidation** : 78% (14/18 documents) ✅
- **Corrections OCR** : 287 entrées
- **Build** : ✅ SUCCESS
- **Données MySQL** :
  - 14 documents consolidés
  - 299 articles extraits
  - 35 signataires

### 🐛 Bugs Résolus

- Fix SQL : `year` → `document_year` (mot réservé)
- Fix pattern "Article premier" : Regex `(?:(1er)|(premier)|(\\d+))`
- Fix test qualité : Seuil 30% → 13% (documents anciens)
- Fix loi-2024-1 : Désactivé (fragment 71-172)

### 🚀 Prochaines Étapes

1. **Tests law-consolidate** : Tests unitaires + intégration pour ConsolidationService
2. **Analyser 4 FAILED** : Documents échoués lors de la consolidation
3. **Améliorer extraction OCR** : Analyser 9 fichiers échouant → Objectif 90%+
4. **law-tojson-app** : Orchestration OCR → IA (fallback)
5. **law-app** : API REST + Swagger pour consultation
6. **Pipeline automatique** : Orchestration complète fetch → consolidate
7. **Enrichir dictionnaire** : Exploiter `data/word_non_recognize.txt` pour ajouter corrections ciblées

---

## Nouvelles Capacités Qualité (Déc 2025)

- `--force=true` sur les jobs pour re-traiter un document sans skip
- Pénalité de séquence des articles (détections : gaps, doublons, ordre)
- Pénalité progressive basée sur mots non reconnus (dictionnaire FR ~336k)
- Fichier de suivi des mots non reconnus : `data/word_non_recognize.txt`
- Logs standardisés : `Recorded X new unrecognized words (total: Y)`

### Commandes utiles

```zsh
# Forcer OCR→JSON et enregistrer les mots non reconnus
java -jar law-app/target/law-app-1.0-SNAPSHOT.jar \
    --job=pdfToJsonJob --doc=decret-2024-1632 --force \
    --spring.main.web-application-type=none

# Vérifier le fichier et statistiques brèves
wc -l data/word_non_recognize.txt
tail -20 data/word_non_recognize.txt
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

## Rappel Final

**Toujours privilégier** :
1. ✅ **Résilience** : Job continue malgré erreurs
2. ✅ **Idempotence** : Re-run safe
3. ✅ **Clean Code** : Exceptions spécifiques, pas null, try-with-resources
4. ✅ **Modularité** : Découpage clair
5. ✅ **Testabilité** : Tests unitaires + intégration

**Workflow** : 1 module à la fois, compile + tests avant suivant

**Objectif** : Architecture propre, maintenable, évolutive ✨
