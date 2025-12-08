# Logique PDF → JSON - Architecture Complète

## Vue d'ensemble

Le système transforme des PDFs de lois/décrets en JSON structuré avec **stratégie de fallback** : IA prioritaire (Ollama → Groq), fallback OCR si indisponible.

### Principe Fondamental : **Idempotence**
- ✅ Ne **JAMAIS** écraser un JSON existant sauf si confiance supérieure
- ✅ Pattern INSERT-ONLY : skip si JSON existe avec bonne confiance
- ✅ Garantit que relancer le job N fois = même résultat que 1 fois

---

## Architecture Modulaire (law-tojson)

```
law-tojson/
├── law-toJsonCommon/       # Interfaces et utilitaires partagés
├── law-pdfToOcr/           # Job 1: PDF → Texte OCR (Tesseract)
├── law-OcrToJson/          # Job 2: OCR → JSON (regex patterns)
├── law-AIpdfToJson/        # Job 3: PDF → JSON (IA Ollama/Groq)
└── law-toJsonApp/          # Application orchestratrice
```

---

## 📊 Workflow Complet

### Phase 1 : Extraction OCR (law-pdfToOcr)
**Job** : `ocrJob`  
**Responsabilité** : Extraire texte brut depuis PDF (PDF → .txt)

```
PDF téléchargé (status=DOWNLOADED)
    ↓
TesseractOcrService
    ↓
    ├─ Extraction directe (PDFBox) → Qualité >= seuil ? ✓ Terminé
    ├─ Sinon OCR via Tesseract :
    │   ├─ Détection magic bytes (PDF, PNG, JPG)
    │   ├─ Conversion pages → images (DPI 300)
    │   ├─ OCR par page (langue: fra)
    │   ├─ Stop si "AMPLIATIONS" détecté
    │   └─ Sauvegarde → data/ocr/{type}/{documentId}.txt
    └─ Gestion corruption (PNG déguisé en PDF)
         → Créer marqueur : # CORRUPTED FILE: {id}
         → Status = CORRUPTED
```

**Fichiers clés** :
- `TesseractOcrService.java` : Service OCR Tesseract
- `ExtractionProcessor.java` : Processor batch (PDF → OCR)
- `DownloadedDocumentReader.java` : Lit documents status=DOWNLOADED
- `ExtractionWriter.java` : Sauvegarde fichiers .txt

**Détection Magic Bytes** :
```java
PDF:     0x25504446 (%PDF)
PNG:     0x89504E47 (‰PNG)
JPG:     0xFFD8FF
UNKNOWN: autres → CORRUPTED
```

**Configuration** :
```yaml
law:
  ocr:
    quality-threshold: 0.7  # Seuil qualité extraction directe
    dpi: 300                # Résolution images OCR
    language: fra           # Langue Tesseract
```

---

### Phase 2 : Transformation JSON (law-tojson)

#### Stratégie Multi-Provider avec Fallback

**Service Orchestrateur** : `PdfToJsonService`

```java
public JsonResult process(LawDocument doc, Path pdfPath, Optional<JsonResult> existingJson) {
    // 1. Check capacités machine
    boolean iaUsable = MachineCapacityUtil.isIaCapable(properties) 
                    && IaAvailabilityChecker.isIaAvailable(properties);
    boolean ocrUsable = MachineCapacityUtil.isOcrCapable(properties);
    
    // 2. Stratégie prioritaire : IA
    if (iaUsable) {
        var result = iaTransformer.transform(doc, pdfPath);
        return pickBetter(existingJson, result);
    }
    
    // 3. Fallback : OCR
    if (ocrUsable) {
        var result = ocrTransformer.transform(doc, pdfPath);
        return pickBetter(existingJson, result);
    }
    
    // 4. Échec : retourner existant ou erreur
    return existingJson.orElseThrow(() -> 
        new IllegalStateException("Aucune capacité disponible pour PDF→JSON"));
}

// Idempotence : ne remplacer que si nouveau résultat significativement meilleur
private JsonResult pickBetter(Optional<JsonResult> existing, JsonResult candidate) {
    if (existing.isEmpty()) {
        return candidate;
    }
    
    JsonResult existingResult = existing.get();
    // Seuil: 0.1 de différence minimum pour remplacer
    if (existingResult.getConfidence() >= candidate.getConfidence() - 0.1) {
        return existingResult;  // Garder existant (idempotent)
    }
    
    return candidate;  // Remplacer par meilleur
}
```

---

### 2.1 Provider IA (law-AIpdfToJson)

**Priorité** : 100 (si disponible)  
**Providers** : Ollama (local) → Groq (cloud)  
**Confidence** : 0.75 - 0.95

#### IaPdfToJsonTransformer

**Workflow** :
```
1. Charger prompt adapté (loi ou décret)
   - pdf-parser.txt pour lois
   - decret-parser.txt pour décrets

2. Lire contenu OCR du document (si disponible)
   
3. Formatter prompt avec texte OCR
   
4. Appel IAProvider (Ollama ou Groq)
   
5. Nettoyer réponse JSON
   
6. Calculer confiance basée sur :
   - Structure JSON valide (+0.15)
   - Présence documentId (+0.15)
   - Présence type (+0.1)
   - Présence title (+0.1)
   - Présence articles array (+0.2)
   - Nombre articles > 0 (+0.15)
   - Nombre articles >= 3 (+0.1)
   - Pénalité si JSON < 100 chars (-0.3)
   - Bonus si source > 2000 chars (+0.1)
   
7. Retourner JsonResult(json, confidence, "IA:OLLAMA" ou "IA:GROQ")
```

**Exemples Prompts** :

```text
# pdf-parser.txt
Tu es un expert en extraction de texte juridique. Analyse le texte suivant 
et extrait les informations sous format JSON :

{
  "documentId": "loi-2024-15",
  "type": "loi",
  "year": 2024,
  "number": 15,
  "title": "Titre complet de la loi",
  "promulgationDate": "2024-06-15",
  "promulgationCity": "Porto-Novo",
  "articles": [
    {
      "number": "1",
      "title": "Article 1er - Objet",
      "content": "Le présent texte..."
    }
  ],
  "signatories": [
    {
      "name": "Patrice TALON",
      "title": "Président de la République",
      "order": 1
    }
  ]
}

Texte à analyser :
{text}
```

**Capacité Requise** :
```yaml
law:
  capacity:
    ia: 4   # Score RAM/CPU minimum (16GB+ RAM, 4+ cores)
    ollama-url: http://localhost:11434
    ollama-models-required: qwen2.5:7b
```

**Détection Disponibilité** :
```java
// MachineCapacityUtil.java
public static boolean isIaCapable(LawProperties props) {
    Runtime rt = Runtime.getRuntime();
    long maxMemoryGB = rt.maxMemory() / (1024 * 1024 * 1024);
    int cores = rt.availableProcessors();
    int score = (int) (maxMemoryGB / 4) + (cores / 2);
    return score >= props.getCapacity().getIa();
}

// IaAvailabilityChecker.java
public static boolean isIaAvailable(LawProperties props) {
    // 1. Ping Ollama
    if (pingOllama(props.getCapacity().getOllamaUrl())) {
        // 2. Vérifier modèles disponibles
        List<String> models = getOllamaModels(props.getCapacity().getOllamaUrl());
        String[] required = props.getCapacity().getOllamaModelsRequired().split(",");
        return Arrays.stream(required).allMatch(models::contains);
    }
    
    // 3. Fallback : vérifier Groq API
    if (props.getGroq().getApiKey() != null && !props.getGroq().getApiKey().isBlank()) {
        return pingGroq(props.getGroq().getBaseUrl());
    }
    
    return false;
}
```

---

### 2.2 Provider OCR (law-OcrToJson)

**Priorité** : 50 (fallback)  
**Confidence** : 0.35 - 0.65

#### OcrPdfToJsonTransformer

**Workflow** :
```
1. Lire fichier PDF
   
2. Extraire texte via PDFBox (texte embédé)
   - Filtrer caractères non-texte
   - Garder lettres, chiffres, ponctuation
   - Retourner si >= 50 chars lisibles
   
3. Construire JSON depuis texte OCR :
   - Extraire articles via regex :
     Pattern: (?i)(?:article|art\.?)\s*(\d+)\s*[:-]?\s*([^.]*\.)?
   - Limiter à 100 articles max
   - Si aucun article trouvé : créer article "0" avec 500 premiers chars
   
4. Calculer confiance :
   - Base: 0.35
   - +0.15 si texte > 500 chars
   - +0.10 si texte > 2000 chars
   - Max: 0.65
   
5. Retourner JsonResult(json, confidence, "OCR:EXTRACTED")
```

**Exemple JSON OCR** :
```json
{
  "documentId": "loi-2024-15",
  "type": "loi",
  "title": "Document loi-2024-15",
  "articles": [
    {
      "number": "1",
      "title": "Article 1",
      "content": "[OCR Extracted Text Placeholder]"
    },
    {
      "number": "2",
      "title": "Article 2",
      "content": "[OCR Extracted Text Placeholder]"
    }
  ]
}
```

**Capacité Requise** :
```yaml
law:
  capacity:
    ocr: 2   # Score RAM/CPU minimum (4GB+ RAM, 2+ cores)
```

---

### 2.3 Extraction Articles via Regex (Alternative)

**Service** : `ArticleExtractorService`  
**Utilisation** : Parsing OCR → Articles structurés

#### Workflow

```
1. Split texte en lignes
   
2. Détecter début/fin articles via patterns :
   - Début: article.start (ex: "Article \d+")
   - Fin: article.end.any (ex: "Fait à|Le Président")
   
3. Accumuler lignes article par article
   
4. Sauvegarder articles (> 10 chars)
   
5. Extraire métadonnées :
   - Titre loi : law.title.start → law.title.end
   - Date promulgation : promulgation.date.pattern
   - Ville : promulgation.city.pattern
   - Signataires : signatory.patterns.X
   
6. Calculer confiance :
   - Score articles (30%) : min(nb_articles/10, 1.0)
   - Score longueur (20%) : min(length/5000, 1.0)
   - Score dictionnaire (30%) : 1.0 - unrecognizedWordsRate
   - Score termes juridiques (20%) : min(termes_trouvés/8, 1.0)
   
7. Retourner ExtractionBundle(articles, metadata, confidence)
```

**Configuration Patterns** (`patterns.properties`) :
```properties
# Détection articles
article.start=(?i)^\\s*Article\\s+\\d+
article.end.any=(?i)(Fait à|Le Président de la République|Ampliations)

# Métadonnées
law.title.start=(?i)LOI\\s+N°
law.title.end=(?i)(L'Assemblée Nationale|EXPOSE DES MOTIFS)
promulgation.date.pattern=(\\d{1,2})(?:er)?\\s+(\\w+)\\s+(\\d{4})
promulgation.city.pattern=Fait à\\s+([A-Z][a-zé-]+)

# Signataires
signatory.patterns.president=Le Président de la République.*Patrice TALON
signatory.patterns.pm=Le Ministre d'État.*Abdoulaye BIO TCHANE
```

**Méthode Unifiée** :
```java
public ExtractionBundle extractAll(String text) {
    List<Article> articles = extractArticles(text);
    DocumentMetadata metadata = extractMetadata(text);
    double confidence = calculateConfidence(text, articles);
    
    return ExtractionBundle.builder()
        .articles(articles)
        .metadata(metadata)
        .confidence(confidence)
        .build();
}
```

---

## 🎯 Jobs Spring Batch

### Job 1 : ocrJob (PDF → OCR)

**Configuration** : `OcrJobConfig.java`

```java
@Bean
public Job ocrJob(Step ocrStep) {
    return new JobBuilder("ocrJob", jobRepository)
        .incrementer(new RunIdIncrementer())
        .start(ocrStep)
        .listener(resourceManagementJobListener)
        .build();
}

@Bean
public Step ocrStep(DownloadedDocumentReader reader,
                    ExtractionProcessor processor,
                    ExtractionWriter writer) {
    return new StepBuilder("ocrStep", jobRepository)
        .<LawDocument, LawDocument>chunk(10, transactionManager)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .build();
}
```

**Composants** :
- **Reader** : `DownloadedDocumentReader` → Lit documents status=DOWNLOADED
- **Processor** : `ExtractionProcessor` → Appelle TesseractOcrService
- **Writer** : `ExtractionWriter` → Sauvegarde .txt

**Idempotence** :
```java
@Override
public LawDocument process(LawDocument document) throws Exception {
    // Skip si OCR existe déjà
    if (fileStorageService.ocrExists(document.getType(), document.getDocumentId())) {
        log.debug("OCR already exists: {}", document.getDocumentId());
        return document;
    }
    
    // Faire OCR...
    byte[] pdfBytes = fileStorageService.readPdf(document.getType(), document.getDocumentId());
    String ocrText = ocrService.extractText(pdfBytes);
    fileStorageService.saveOcr(document.getType(), document.getDocumentId(), ocrText);
    
    return document;
}
```

---

### Job 2 : articleExtractionJob (OCR/PDF → JSON)

**Configuration** : `ArticleExtractionJobConfig.java`

```java
@Bean
public Job articleExtractionJob(Step articleExtractionStep) {
    return new JobBuilder("articleExtractionJob", jobRepository)
        .incrementer(new RunIdIncrementer())
        .start(articleExtractionStep)
        .build();
}

@Bean
public Step articleExtractionStep(DownloadedDocumentReader reader,
                                  OcrJsonProcessor processor) {
    return new StepBuilder("articleExtractionStep", jobRepository)
        .<LawDocument, LawDocument>chunk(1, transactionManager)
        .reader(reader)
        .processor(processor)
        .build();
}
```

**Composants** :
- **Reader** : `DownloadedDocumentReader` → Lit documents avec PDF
- **Processor** : `OcrJsonProcessor` → Appelle PdfToJsonService
- **Writer** : Intégré dans processor (sauvegarde JSON)

**Processor Logic** :
```java
@Override
public LawDocument process(LawDocument document) throws Exception {
    String pdfPath = document.getPdfPath();
    String fileName = Paths.get(pdfPath).getFileName().toString();
    
    // 1. Lire JSON existant (idempotence)
    Optional<JsonResult> existing = readExistingJson(fileName);
    
    // 2. Appeler service avec stratégie IA → OCR
    JsonResult result = pdfToJsonService.process(document, Paths.get(pdfPath), existing);
    
    // 3. Sauvegarder JSON (si meilleur)
    saveJsonOutput(result.getJson(), fileName);
    
    log.info("JSON generated ({}): {}", result.getSource(), fileName);
    return document;
}
```

**Stratégie Sauvegarde** :
```java
private void saveJsonOutput(String json, String pdfFileName) {
    String baseName = pdfFileName.replace(".pdf", "");
    Path outputPath = Paths.get("data/articles/loi", baseName + ".json");
    
    Files.createDirectories(outputPath.getParent());
    Files.writeString(outputPath, json);
}
```

---

## 📁 Structure Fichiers

```
data/
├── pdfs/
│   ├── loi/
│   │   ├── loi-2024-15.pdf
│   │   └── loi-2024-16.pdf
│   └── decret/
│       └── decret-2024-100.pdf
├── ocr/
│   ├── loi/
│   │   ├── loi-2024-15.txt
│   │   └── loi-2024-16.txt
│   └── decret/
│       └── decret-2024-100.txt
└── articles/
    ├── loi/
    │   ├── loi-2024-15.json
    │   └── loi-2024-16.json
    └── decret/
        └── decret-2024-100.json
```

---

## 🔧 Configuration Complète

```yaml
law:
  # Capacités machine
  capacity:
    ia: 4   # Score minimum pour IA (16GB+ RAM, 4+ cores)
    ocr: 2  # Score minimum pour OCR (4GB+ RAM, 2+ cores)
    ollama-url: http://localhost:11434
    ollama-models-required: qwen2.5:7b
  
  # Configuration OCR
  ocr:
    quality-threshold: 0.7  # Seuil qualité extraction directe
    dpi: 300                # Résolution images OCR
    language: fra           # Langue Tesseract
  
  # API Groq (fallback)
  groq:
    api-key: ${GROQ_API_KEY:}
    base-url: https://api.groq.com/openai/v1
  
  # Stockage
  storage:
    base-path: data
    pdf-dir: pdfs
    ocr-dir: ocr
    json-dir: articles
```

---

## 🎨 Format JSON Attendu

```json
{
  "_metadata": {
    "confidence": 0.85,
    "method": "IA_OLLAMA",
    "timestamp": "2025-12-06T10:30:00Z"
  },
  "documentId": "loi-2024-15",
  "type": "loi",
  "year": 2024,
  "number": 15,
  "title": "Loi n° 2024-15 portant révision de la Constitution",
  "promulgationDate": "2024-06-15",
  "promulgationCity": "Porto-Novo",
  "articles": [
    {
      "number": "1",
      "title": "Article 1er - Objet",
      "content": "Le présent texte porte révision de la Constitution..."
    },
    {
      "number": "2",
      "title": "Article 2 - Champ d'application",
      "content": "Les dispositions de la présente loi s'appliquent..."
    }
  ],
  "signatories": [
    {
      "name": "Patrice TALON",
      "title": "Président de la République",
      "order": 1
    },
    {
      "name": "Abdoulaye BIO TCHANE",
      "title": "Ministre d'État chargé du Développement",
      "order": 2
    }
  ]
}
```

---

## ⚙️ Règles d'Idempotence

### Principe : Ne JAMAIS Écraser Sans Justification

```java
// ✅ BIEN : Comparer confiance avant écrasement
if (existingJson.isPresent()) {
    JsonResult existing = existingJson.get();
    if (newConfidence <= existing.getConfidence()) {
        log.info("⏭️ Keeping existing JSON (better confidence: {})", 
                 existing.getConfidence());
        return existing;
    }
}

// Sauvegarder nouveau JSON (meilleur)
Files.writeString(jsonPath, newJson);
log.info("✅ Saved JSON with confidence {}", newConfidence);
```

### Seuil Remplacement : 0.1

```java
// Ne remplacer que si différence significative (> 0.1)
if (existingConfidence >= candidateConfidence - 0.1) {
    return existing;  // Garder existant
}
return candidate;  // Remplacer
```

### Format Metadata

```json
{
  "_metadata": {
    "confidence": 0.85,
    "method": "IA_OLLAMA | IA_GROQ | OCR",
    "timestamp": "2025-12-06T10:30:00Z",
    "previousConfidence": 0.75
  }
}
```

---

## 🚨 Gestion Erreurs

### Fichiers Corrompus

**Détection** :
```java
// TesseractOcrService.java
byte[] magic = Arrays.copyOf(pdfBytes, 4);

if (magic[0] == 0x25 && magic[1] == 0x50 && magic[2] == 0x44 && magic[3] == 0x46) {
    // PDF valide
} else if (magic[0] == (byte)0x89 && magic[1] == 0x50 && magic[2] == 0x4E && magic[3] == 0x47) {
    // PNG déguisé en PDF
    throw new CorruptedFileException("PNG disguised as PDF");
} else if (magic[0] == (byte)0xFF && magic[1] == (byte)0xD8 && magic[2] == (byte)0xFF) {
    // JPG déguisé en PDF
    throw new CorruptedFileException("JPG disguised as PDF");
} else {
    // Format inconnu
    throw new CorruptedFileException("Unknown file format");
}
```

**Traitement** :
```java
try {
    String text = tesseractOcrService.extractText(pdfBytes);
    fileStorageService.saveOcr(type, documentId, text);
} catch (CorruptedFileException e) {
    // Créer marqueur corruption
    String marker = String.format(
        "# CORRUPTED FILE: %s%n# Error: %s%n# Date: %s%n",
        documentId, e.getMessage(), LocalDateTime.now()
    );
    fileStorageService.saveOcr(type, documentId, marker);
    document.setStatus(ProcessingStatus.CORRUPTED);
    return document; // Continue job
}
```

### Erreurs IA

**Retry avec Fallback** :
```java
try {
    // Tentative Ollama
    return ollamaProvider.generateText(prompt, null);
} catch (Exception e) {
    log.warn("Ollama failed, trying Groq: {}", e.getMessage());
    
    try {
        // Fallback Groq
        return groqProvider.generateText(prompt, null);
    } catch (Exception e2) {
        log.error("All IA providers failed, using OCR fallback");
        // Fallback final : OCR
        return ocrTransformer.transform(document, pdfPath);
    }
}
```

---

## 📊 Métriques & Monitoring

### Logging Standardisé

```java
// Début transformation
log.info("🔄 Processing PDF→JSON: {} (method: {})", documentId, method);

// Succès
log.info("✅ JSON generated: {} (confidence: {:.2f}, source: {})", 
         documentId, confidence, source);

// Échec
log.error("❌ Transformation failed: {} (reason: {})", documentId, reason);

// Idempotence
log.info("⏭️ Keeping existing JSON: {} (confidence: {:.2f} >= {:.2f})", 
         documentId, existingConfidence, newConfidence);
```

### Métriques par Provider

```java
// Compteurs
AtomicInteger ollamaSuccess = new AtomicInteger();
AtomicInteger groqSuccess = new AtomicInteger();
AtomicInteger ocrFallback = new AtomicInteger();

// Durées
Duration ollamaDuration = Duration.ZERO;
Duration groqDuration = Duration.ZERO;
Duration ocrDuration = Duration.ZERO;

// Rapport
log.info("""
    Transformation stats:
    - Ollama: {} success ({} avg)
    - Groq: {} success ({} avg)
    - OCR fallback: {} ({} avg)
    """, 
    ollamaSuccess.get(), ollamaDuration.dividedBy(ollamaSuccess.get()),
    groqSuccess.get(), groqDuration.dividedBy(groqSuccess.get()),
    ocrFallback.get(), ocrDuration.dividedBy(ocrFallback.get())
);
```

---

## 🔗 Dépendances Maven

### law-pdfToOcr

```xml
<dependencies>
    <dependency>law-common</dependency>
    <dependency>spring-boot-starter-batch</dependency>
    <dependency>pdfbox (3.0.0)</dependency>
    <dependency>tesseract-platform (5.3.3-1.5.10)</dependency>
</dependencies>
```

### law-AIpdfToJson

```xml
<dependencies>
    <dependency>law-common</dependency>
    <dependency>spring-boot-starter-web</dependency>
    <dependency>spring-ai-ollama</dependency>
    <dependency>okhttp (4.12.0)</dependency>
    <dependency>gson</dependency>
</dependencies>
```

### law-OcrToJson

```xml
<dependencies>
    <dependency>law-common</dependency>
    <dependency>spring-boot-starter-batch</dependency>
    <dependency>gson</dependency>
</dependencies>
```

---

## 🎯 Anti-Patterns à Éviter

### ❌ Écraser Aveuglément

```java
// ❌ MAL
Files.writeString(jsonPath, newJson);

// ✅ BIEN
if (!existingJson.isPresent() || newConfidence > existingJson.get().getConfidence() + 0.1) {
    Files.writeString(jsonPath, newJson);
}
```

### ❌ Ignorer Capacités Machine

```java
// ❌ MAL
IAProvider provider = new OllamaProvider();  // Peut échouer si RAM insuffisante

// ✅ BIEN
if (MachineCapacityUtil.isIaCapable(properties)) {
    IAProvider provider = new OllamaProvider();
} else {
    log.warn("Insufficient capacity for IA, using OCR fallback");
}
```

### ❌ Arrêter Job sur Erreur

```java
// ❌ MAL
throw new RuntimeException("OCR failed");  // Arrête tout le job

// ✅ BIEN
document.setStatus(ProcessingStatus.FAILED);
return document;  // Continue avec document suivant
```

---

## 📝 Checklist Migration

### law-pdfToOcr ✅
- [x] TesseractOcrService.java
- [x] ExtractionProcessor.java
- [x] DownloadedDocumentReader.java
- [x] ExtractionWriter.java
- [x] OcrJobConfig.java

### law-OcrToJson ⏳
- [ ] ArticleExtractorService.java
- [ ] ArticleExtractionProcessor.java
- [ ] ArticleExtractionWriter.java
- [ ] OcrPdfToJsonTransformer.java
- [ ] RegexPatternConfig.java
- [ ] ArticleExtractionJobConfig.java

### law-AIpdfToJson ⏳
- [ ] IAProvider.java (interface)
- [ ] OllamaProvider.java
- [ ] GroqProvider.java
- [ ] IaPdfToJsonTransformer.java
- [ ] IaAvailabilityChecker.java
- [ ] MachineCapacityUtil.java

### law-toJsonCommon ⏳
- [ ] PdfToJsonTransformer.java (interface)
- [ ] PdfToJsonService.java (orchestrateur)
- [ ] JsonResult.java (DTO)

### law-toJsonApp ⏳
- [ ] LawToJsonApplication.java
- [ ] Application configuration
- [ ] Scheduler jobs

---

## 🚀 Rappel Final

**Priorités** :
1. ✅ **Idempotence** : Ne jamais écraser sans justification
2. ✅ **Résilience** : Fallback IA → OCR automatique
3. ✅ **Performance** : Détection capacités machine
4. ✅ **Qualité** : Confiance >= 0.7 recommandée
5. ✅ **Monitoring** : Logs standardisés avec emojis

**Workflow Idéal** :
```
PDF téléchargé
    ↓
ocrJob (PDF → OCR .txt)
    ↓
articleExtractionJob (PDF → JSON via IA/OCR)
    ↓
consolidationJob (JSON → Base de données)
```

**Règle d'Or** : **Relancer N fois = même résultat que 1 fois** ✨
