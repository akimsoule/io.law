# GitHub Copilot Instructions - io.law (Architecture Multi-Modules)

## Architecture du Projet

### Vue d'ensemble
Application Spring Batch modulaire pour extraire, traiter et consolider les lois et décrets du gouvernement béninois depuis https://sgg.gouv.bj/doc.

**Migration en cours** : Transformation du projet monolithique `law.spring` vers une architecture multi-modules `io.law`.

### Technologies
- **Java 17+** avec pattern matching, records, text blocks
- **Spring Boot 3.2.0** + Spring Batch
- **Maven Multi-Modules** (7 modules)
- **PDFBox** pour extraction PDF
- **Tesseract OCR** (via JavaCPP) pour OCR des PDFs scannés
- **MySQL 8.4** (Docker) pour persistance
- **Ollama** (optionnel) pour parsing IA en local
- **Groq API** (optionnel) pour parsing IA cloud (fallback)

### Structure Multi-Modules

```
io.law/
├── pom.xml (parent)
├── law-common/          # Socle commun (modèles, repos, exceptions, config)
├── law-fetch/           # Jobs de récupération métadonnées
├── law-download/        # Job de téléchargement PDFs
├── law-tojson/          # Transformation PDF → JSON
│   ├── law-AIpdfToJson/    # Extraction via IA
│   ├── law-pdfToOcr/       # Extraction OCR
│   ├── law-OcrToJson/      # Parsing OCR → JSON
│   └── law-toJsonApp/      # Orchestration
├── law-consolidate/     # Job de consolidation BD
└── law-api/             # API REST, scheduler, orchestration
```

### Modules Détaillés

#### 1. law-common (Socle Partagé)
**Responsabilité** : Composants réutilisables par tous les modules

**Contenu** :
- **model/** : Entités JPA
  - `LawDocument` : Document principal (loi/décret) avec annotations JPA complètes
  - `FetchResult` : Résultat fetch HTTP (déplacé vers law-fetch)
  - `FetchCursor` : Position scan années précédentes (déplacé vers law-fetch)
  - `FetchNotFoundRange` : Plages 404 détectées (déplacé vers law-fetch)
  - Note : `Article`, `Signatory`, `DocumentMetadata`, etc. déplacés vers law-tojson/law-toJsonCommon
  
- **repository/** : Repositories JPA
  - `LawDocumentRepository` : CRUD + requêtes spécialisées
  - `FetchResultRepository` : (déplacé vers law-fetch)
  - `FetchCursorRepository` : (déplacé vers law-fetch)
  - `FetchNotFoundRangeRepository` : (déplacé vers law-fetch)
  
- **exception/** : Exceptions métier (21 exceptions)
  - `DocumentNotFoundException`
  - `DownloadException`
  - `OcrException`
  - `IAException`
  - `CorruptedFileException`
  - etc.
  
- **config/** : Configuration Spring
  - `LawProperties` : Properties YAML
  - `GsonConfig` : Configuration Gson
  - `DatabaseConfig` : Configuration JPA/MySQL (Docker)
  
- **service/** : Services métier
  - `FileStorageService` : Gestion chemins fichiers (PDF/OCR/JSON paths)
  - `DocumentStatusManager` : Mise à jour statuts documents
  
- **util/** : Utilitaires
  - `DateUtils` : Manipulation dates
  - `StringUtils` : Nettoyage texte
  - `ValidationUtils` : Validations

**Dépendances** :
```xml
<dependencies>
    <dependency>spring-boot-starter-data-jpa</dependency>
    <dependency>spring-boot-starter-batch</dependency>
    <dependency>mysql-connector-j</dependency>
    <dependency>spring-boot-starter-validation</dependency>
    <dependency>gson</dependency>
    <dependency>lombok</dependency>
</dependencies>
```

#### 2. law-fetch (Récupération Métadonnées)
**Responsabilité** : Scanner le site SGG et détecter les documents disponibles

**Jobs** :
1. **fetchCurrentJob** : Scan année courante (numéros 1-2000)
2. **fetchPreviousJob** : Scan années 1960 à année-1 avec cursor

**Composants** :
- **reader/**
  - `CurrentYearLawDocumentReader` : Génère documents année courante
  - `PreviousYearsLawDocumentReader` : Lit depuis cursor
  - `LawDocumentReader` : Classe abstraite commune
  
- **processor/**
  - `FetchProcessor` : Vérifie existence HTTP (HEAD request)
  
- **writer/**
  - `FetchWriter` : Sauvegarde métadonnées + cursor
  
- **service/**
  - `LawFetchService` : HTTP client avec retry
  - `NotFoundRangeService` : Détection plages 404

**Configuration** :
- Chunk size : 10 items
- Threads : 10 concurrents
- Timeout fetchPreviousJob : restart automatique
- Déclenchement : Manuel via API REST

**Dépendances** :
```xml
<dependencies>
    <dependency>law-common</dependency>
    <dependency>spring-boot-starter-batch</dependency>
    <dependency>spring-boot-starter-web</dependency>
</dependencies>
```

#### 3. law-download (Téléchargement PDFs) ✅
**Responsabilité** : Télécharger les PDFs des documents FETCHED

**Job** : `downloadJob`

**Composants** :
- **reader/**
  - `FetchedDocumentReader` : Lit documents status=FETCHED avec support mode ciblé + force
  
- **processor/**
  - `DownloadProcessor` : Télécharge PDF avec Apache HttpClient 5
  
- **writer/**
  - `FileDownloadWriter` : Sauvegarde PDF sur disque + table `download_results`
  
- **model/**
  - `DownloadResult` : Entité JPA pour tracking téléchargements
  
- **repository/**
  - `DownloadResultRepository` : Persistance résultats téléchargements
  
- **service/**
  - `PdfDownloadService` : Gestion téléchargement + validation

**Stratégie** :
- Téléchargement : Apache HttpClient 5 avec SHA-256 hashing
- Stockage : FileStorageService pour chemins normalisés
- Idempotence : Check `download_results` avant re-téléchargement
- Modes : scan complet, document ciblé, mode force
- Statut : DOWNLOADED ou FAILED

**Tests** : 8 tests (2 intégration + 6 unitaires) ✅

**Dépendances** :
```xml
<dependencies>
    <dependency>law-common</dependency>
    <dependency>spring-boot-starter-batch</dependency>
    <dependency>spring-boot-starter-web</dependency>
    <dependency>commons-io</dependency>
</dependencies>
```

#### 4. law-tojson (Transformation PDF → JSON)
**Responsabilité** : Extraire contenu structuré des PDFs

**Architecture** : 4 sous-modules avec stratégie de fallback

##### 4.1 law-pdfToOcr (Extraction OCR)
**Job** : `ocrJob`

**Composants** :
- **reader/** : `DownloadedDocumentReader` (status=DOWNLOADED)
- **processor/** : `ExtractionProcessor` (Tesseract OCR)
- **writer/** : `ExtractionWriter` (fichiers .txt)
- **service/** : `TesseractOcrService`

**Détection Magic Bytes** :
```java
PDF:     0x25504446 (%PDF)
PNG:     0x89504E47 (‰PNG)
JPG:     0xFFD8FF
UNKNOWN: autres → CORRUPTED
```

**Gestion Corruption** :
```java
// Créer marqueur si corrompu
if (corrupted) {
    String marker = String.format(
        "# CORRUPTED FILE: %s%n# Error: %s%n# Date: %s%n",
        documentId, errorMessage, LocalDateTime.now()
    );
    Files.writeString(ocrPath, marker);
    document.setStatus(ProcessingStatus.CORRUPTED);
    return document; // Continue job
}
```

**Dépendances** :
```xml
<dependencies>
    <dependency>law-common</dependency>
    <dependency>spring-boot-starter-batch</dependency>
    <dependency>pdfbox</dependency>
    <dependency>javacpp-tesseract</dependency>
</dependencies>
```

##### 4.2 law-OcrToJson (Parsing OCR → JSON)
**Job** : `articleExtractionJob`

**Composants** :
- **reader/** : `OcrFileReader` (lit fichiers .txt)
- **processor/** : `ArticleExtractionProcessor` (regex patterns)
- **writer/** : `ArticleExtractionWriter` (fichiers .json)
- **service/** : `ArticleParsingService`

**Format JSON Attendu** :
```json
{
  "_metadata": {
    "confidence": 0.75,
    "method": "OCR",
    "timestamp": "2025-12-05T10:30:00Z"
  },
  "type": "loi",
  "year": 2024,
  "number": 15,
  "title": "Loi portant...",
  "articles": [
    {
      "number": "1",
      "content": "Le présent texte...",
      "title": "Article 1er - Objet"
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
```

##### 4.3 law-AIpdfToJson (Extraction IA)
**Responsabilité** : Extraction via Ollama ou Groq API

**Stratégie Fallback** (ordre priorité) :
1. **IA Ollama (priorité 100)** : Si ping OK + modèles disponibles + capacité IA ≥4
2. **IA Groq (priorité 80)** : Si API key configurée + serveur répond
3. **OCR (priorité 50)** : Si capacité OCR ≥2

**Règle Écrasement** :
```java
// Ne JAMAIS écraser si confiance existante supérieure
if (existingJson.isPresent()) {
    double existingConfidence = existingJson.get().getMetadata().getConfidence();
    if (newConfidence <= existingConfidence) {
        log.info("Keeping existing JSON (better confidence: {})", existingConfidence);
        return existingJson.get();
    }
}
```

**Composants** :
- **service/**
  - `IAProvider` : Interface commune
  - `OllamaProvider` : Client Ollama
  - `GroqProvider` : Client Groq
  - `CapacityDetectionService` : Détection capacités machine

**Configuration** :
```yaml
law:
  capacity:
    ia: 4   # Score RAM/CPU minimum pour IA (16GB+ RAM)
    ocr: 2  # Score pour OCR (4GB+ RAM)
    ollama-url: http://localhost:11434
    ollama-models-required: qwen2.5:7b
```

**Dépendances** :
```xml
<dependencies>
    <dependency>law-common</dependency>
    <dependency>spring-boot-starter-web</dependency>
    <dependency>spring-ai-ollama</dependency>
    <dependency>okhttp</dependency>
</dependencies>
```

##### 4.4 law-toJsonApp (Orchestration)
**Responsabilité** : Application Spring Boot autonome orchestrant les 3 modules

**Contenu** :
- Main class : `LawToJsonApplication`
- Configuration : Séquence ocrJob → articleExtractionJob → iaJob
- Déclenchement : Manuel via API ou ligne de commande
- Monitoring : Logs consolidés

#### 5. law-consolidate (Consolidation BD)
**Responsabilité** : Importer JSON structurés dans MySQL (Docker)

**Job** : `consolidationJob`

**Composants** :
- **reader/** : `ConsolidationReader` (lit fichiers .json)
- **processor/** : `ConsolidationProcessor` (validation + mapping)
- **writer/** : `ConsolidationWriter` (sauvegarde JPA)
- **service/** : `ConsolidationService`

**Workflow** :
```
JSON files → Parse/Validate → Map to entities → Save to DB
```

**Entités Créées** :
- `LawDocument` (update status=CONSOLIDATED)
- `Article` (bulk insert)
- `Signatory` (bulk insert)
- `Metadata` (update confidence/method)

**Base de données** :
- MySQL 8.4 dans Docker
- Commandes : `docker exec -it mysql-law mysql -u root -p law_db`

**Dépendances** :
```xml
<dependencies>
    <dependency>law-common</dependency>
    <dependency>spring-boot-starter-batch</dependency>
    <dependency>gson</dependency>
</dependencies>
```

#### 6. law-api (API REST & Orchestration)
**Responsabilité** : Exposition API REST + Orchestration manuelle des jobs + CLI

**Contenu** :
- **controller/**
  - `BatchController` : Endpoints jobs (`POST /jobs/{jobName}/run`)
  - `LawDocumentController` : API REST CRUD
  - `SearchController` : Recherche full-text
  
- **cli/**
  - `JobCommandLineRunner` : Exécution jobs via arguments CLI
  - Support : `--job=fetchCurrentJob --params=key=value`
  
- **config/**
  - `FullPipelineJobConfig` : Séquence complète (fetch → download → ocr → extract → consolidate)
  - `OpenApiConfig` : Swagger/OpenAPI
  - `GlobalExceptionHandler` : Gestion erreurs HTTP
  
- **service/**
  - `JobLauncherService` : Lancement jobs programmatique
  - `JobMonitoringService` : Suivi exécutions

**Endpoints API** :
```
POST   /api/jobs/fetchCurrentJob/run
POST   /api/jobs/fetchPreviousJob/run
POST   /api/jobs/downloadJob/run
POST   /api/jobs/ocrJob/run
POST   /api/jobs/articleExtractionJob/run
POST   /api/jobs/consolidationJob/run
POST   /api/jobs/fullPipelineJob/run

GET    /api/laws?type=loi&year=2024
GET    /api/laws/{id}
GET    /api/laws/search?q=budget
```

**Exécution CLI** :
```bash
# Lancer un job spécifique
java -jar law-api.jar --job=fetchCurrentJob

# Job avec paramètres
java -jar law-api.jar --job=fetchPreviousJob --year=2024

# Pipeline complet
java -jar law-api.jar --job=fullPipelineJob

# Mode headless (sans serveur web)
java -jar law-api.jar --job=downloadJob
```

**Dépendances** :
```xml
<dependencies>
    <dependency>law-common</dependency>
    <dependency>law-fetch</dependency>
    <dependency>law-download</dependency>
    <dependency>law-tojson</dependency>
    <dependency>law-consolidate</dependency>
    <dependency>spring-boot-starter-web</dependency>
    <dependency>spring-boot-starter-actuator</dependency>
    <dependency>springdoc-openapi-starter-webmvc-ui</dependency>
</dependencies>
```

---

## Principes de Clean Code STRICTS

### 1. Gestion des Exceptions
❌ **INTERDIT** : `throws Exception`, `catch (Exception e)`
✅ **OBLIGATOIRE** : Exceptions spécifiques

```java
// ❌ MAL
public void process() throws Exception { }

// ✅ BIEN
public void process() throws IOException, IAException { }
```

### 2. Retours Null
❌ **INTERDIT** : `return null`
✅ **OBLIGATOIRE** : `Optional<T>`, collections vides, objets par défaut

```java
// ❌ MAL
public String getText() { return null; }

// ✅ BIEN
public Optional<String> getText() { return Optional.empty(); }
public List<String> getTexts() { return Collections.emptyList(); }
```

### 3. Constantes vs Littéraux
❌ **INTERDIT** : Littéraux dupliqués (>2 fois)
✅ **OBLIGATOIRE** : Constantes privées

```java
// ❌ MAL
if (status.equals("CORRUPTED")) { }
if (status.equals("CORRUPTED")) { }

// ✅ BIEN
private static final String STATUS_CORRUPTED = "CORRUPTED";
if (status.equals(STATUS_CORRUPTED)) { }
```

### 4. Gestion des Ressources
❌ **INTERDIT** : Streams/Files sans fermeture
✅ **OBLIGATOIRE** : try-with-resources

```java
// ❌ MAL
Stream<Path> paths = Files.walk(dir);
paths.forEach(...);

// ✅ BIEN
try (Stream<Path> paths = Files.walk(dir)) {
    paths.forEach(...);
}
```

### 5. Format Strings Multi-plateforme
❌ **INTERDIT** : `\n` dans String.format
✅ **OBLIGATOIRE** : `%n` pour indépendance plateforme

```java
// ❌ MAL
String.format("Line1\nLine2\n")

// ✅ BIEN
String.format("Line1%nLine2%n")
```

---

## Règles d'Idempotence des Jobs

### Principe Fondamental
**TOUTE** opération batch DOIT être idempotente :
- Relancer un job N fois = même résultat que 1 fois
- Ne pas refaire ce qui est déjà fait
- Ne pas écraser sauf si amélioration prouvée

### Implémentation

```java
// ✅ BIEN : Check avant traitement
@Override
public LawDocument process(LawDocument document) {
    if (document.getStatus() == ProcessingStatus.EXTRACTED) {
        log.debug("Already extracted, skipping: {}", document.getDocumentId());
        return document;
    }
    
    // Traitement...
    return processedDocument;
}

// ✅ BIEN : N'écraser JSON que si confiance supérieure
public void saveJson(String documentId, JsonData newData) {
    Optional<JsonData> existing = readExistingJson(documentId);
    
    if (existing.isEmpty() || newData.confidence() > existing.get().confidence()) {
        Files.writeString(jsonPath(documentId), toJson(newData));
        log.info("✅ Saved JSON with confidence {}", newData.confidence());
    } else {
        log.info("⏭️ Keeping existing JSON (better confidence: {})", 
                 existing.get().confidence());
    }
}
```

### Statuts de Documents

```java
public enum ProcessingStatus {
    PENDING,      // Créé, pas encore traité
    FETCHED,      // Métadonnées récupérées (HEAD 200)
    DOWNLOADED,   // PDF téléchargé
    EXTRACTED,    // OCR effectué (fichier .txt créé)
    CONSOLIDATED, // Données en base MySQL
    FAILED,       // Erreur générique
    CORRUPTED     // PDF corrompu (PNG déguisé, tronqué, etc.)
}
```

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
        // 1. Check idempotence
        if (alreadyProcessed(item)) {
            log.debug("⏭️ Already processed, skipping: {}", item.getId());
            return convertToOutput(item);
        }
        
        // 2. Process with error handling
        try {
            OutputType result = service.doProcess(item);
            result.setStatus(ProcessingStatus.SUCCESS);
            log.info("✅ Processed: {}", item.getId());
            return result;
            
        } catch (SpecificException e) {
            log.error("❌ Processing failed for {}: {}", item.getId(), e.getMessage());
            item.setStatus(ProcessingStatus.FAILED);
            return convertToOutput(item); // Don't stop job
        }
    }
    
    private boolean alreadyProcessed(InputType item) {
        return item.getStatus() == ProcessingStatus.SUCCESS;
    }
}
```

### FileStorageService Pattern

```java
@Service
@RequiredArgsConstructor
public class MyService {
    
    private final FileStorageService fileStorageService;
    
    public void processDocument(LawDocument document) {
        String docId = document.getDocumentId(); // "loi-2024-15"
        
        // ✅ BIEN : Utiliser FileStorageService
        Path pdfPath = fileStorageService.pdfPath(document.getType(), docId);
        Path ocrPath = fileStorageService.ocrPath(document.getType(), docId);
        Path jsonPath = fileStorageService.jsonPath(document.getType(), docId);
        
        // ✅ BIEN : Vérifier existence
        if (!fileStorageService.pdfExists(document.getType(), docId)) {
            throw new DocumentNotFoundException("PDF not found: " + docId);
        }
        
        // ❌ MAL : Construire chemins manuellement
        // Path pdfPath = Paths.get("data/pdfs/loi/" + docId + ".pdf");
    }
}
```

### Conditional Beans Pattern

```java
// Beans IA conditionnels (éviter erreurs si IAProvider absent)
@Configuration
@ConditionalOnBean(IAProvider.class)
@ConditionalOnProperty(name = "law.capacity.ia", havingValue = "4", matchIfMissing = false)
public class IaJobConfiguration {
    
    @Bean
    public Job iaExtractionJob(Step iaExtractionStep) {
        return new JobBuilder("iaExtractionJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(iaExtractionStep)
            .build();
    }
}
```

---

## Configuration Multi-Modules

### POM Parent (io.law/pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>
    
    <groupId>bj.gouv.sgg</groupId>
    <artifactId>io.law</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    
    <modules>
        <module>law-common</module>
        <module>law-fetch</module>
        <module>law-download</module>
        <module>law-tojson</module>
        <module>law-consolidate</module>
        <module>law-api</module>
    </modules>
    
    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    
    <dependencyManagement>
        <dependencies>
            <!-- Modules internes -->
            <dependency>
                <groupId>bj.gouv.sgg</groupId>
                <artifactId>law-common</artifactId>
                <version>${project.version}</version>
            </dependency>
            
            <!-- Libraries externes -->
            <dependency>
                <groupId>com.google.code.gson</groupId>
                <artifactId>gson</artifactId>
                <version>2.10.1</version>
            </dependency>
            
            <dependency>
                <groupId>org.apache.pdfbox</groupId>
                <artifactId>pdfbox</artifactId>
                <version>3.0.0</version>
            </dependency>
            
            <dependency>
                <groupId>org.bytedeco</groupId>
                <artifactId>tesseract-platform</artifactId>
                <version>5.3.3-1.5.10</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

### POM Module Type (law-fetch/pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>bj.gouv.sgg</groupId>
        <artifactId>io.law</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    
    <artifactId>law-fetch</artifactId>
    <packaging>jar</packaging>
    
    <dependencies>
        <!-- Module interne -->
        <dependency>
            <groupId>bj.gouv.sgg</groupId>
            <artifactId>law-common</artifactId>
        </dependency>
        
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-batch</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        
        <!-- Tests -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.batch</groupId>
            <artifactId>spring-batch-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

---

## Application Properties

### application.yml (law-api ou law-toJsonApp)

```yaml
spring:
  application:
    name: io.law
  
  datasource:
    url: jdbc:mysql://localhost:3306/law_db?useUnicode=true&characterEncoding=utf8mb4
    username: ${DATABASE_USERNAME:root}
    password: ${DATABASE_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver
  
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
        format_sql: true
  
  batch:
    jdbc:
      initialize-schema: always
    job:
      enabled: false  # Désactiver auto-start (scheduler manuel)

law:
  base-url: https://sgg.gouv.bj/doc
  user-agent: Mozilla/5.0 (compatible; LawBatchBot/1.0)
  
  storage:
    base-path: /data
    pdf-dir: pdfs
    ocr-dir: ocr
    json-dir: articles
  
  http:
    timeout: 30000  # 30 secondes
    max-retries: 3
    retry-delay: 2000  # 2 secondes
  
  batch:
    chunk-size: 10
    max-threads: 10
    max-documents-to-extract: 50
    max-items-to-fetch-previous: 100
    job-timeout-minutes: 55
  
  capacity:
    ia: 4   # Score RAM/CPU pour IA (16GB+ RAM)
    ocr: 2  # Score pour OCR (4GB+ RAM)
    ollama-url: http://localhost:11434
    ollama-models-required: qwen2.5:7b
  
  groq:
    api-key: ${GROQ_API_KEY:}
    base-url: https://api.groq.com/openai/v1

logging:
  level:
    root: INFO
    bj.gouv.sgg: DEBUG
    org.springframework.batch: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
```

---

## Tests

### Structure Tests

```
src/test/java/
├── bj/gouv/sgg/
│   ├── batch/
│   │   ├── reader/
│   │   │   └── CurrentYearLawDocumentReaderTest.java
│   │   ├── processor/
│   │   │   └── FetchProcessorTest.java
│   │   └── writer/
│   │       └── FetchWriterTest.java
│   ├── service/
│   │   ├── LawFetchServiceTest.java
│   │   └── NotFoundRangeServiceTest.java
│   └── integration/
│       └── FetchJobIntegrationTest.java
```

### Test Unitaire Pattern

```java
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.yml")
class FetchProcessorTest {
    
    @Autowired
    private FetchProcessor processor;
    
    @MockBean
    private LawFetchService fetchService;
    
    @Test
    void testProcessExistingDocument() throws Exception {
        // Given
        LawDocument doc = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .status(ProcessingStatus.PENDING)
            .build();
        
        when(fetchService.checkDocumentExists("loi", 2024, 15))
            .thenReturn(true);
        
        // When
        LawDocument result = processor.process(doc);
        
        // Then
        assertNotNull(result);
        assertEquals(ProcessingStatus.FETCHED, result.getStatus());
        verify(fetchService).checkDocumentExists("loi", 2024, 15);
    }
    
    @Test
    void testProcessNonExistingDocument() throws Exception {
        // Given
        LawDocument doc = LawDocument.builder()
            .type("loi")
            .year(1960)
            .number(999)
            .status(ProcessingStatus.PENDING)
            .build();
        
        when(fetchService.checkDocumentExists("loi", 1960, 999))
            .thenReturn(false);
        
        // When
        LawDocument result = processor.process(doc);
        
        // Then
        assertNull(result); // Filtered out
    }
}
```

### Test Intégration Job

```java
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.yml")
class FetchJobIntegrationTest {
    
    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    
    @Autowired
    private LawDocumentRepository repository;
    
    @Test
    void testFetchCurrentJobExecution() throws Exception {
        // Given
        repository.deleteAll();
        
        // When
        JobExecution jobExecution = jobLauncherTestUtils.launchJob();
        
        // Then
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());
        
        long fetchedCount = repository.countByStatus(ProcessingStatus.FETCHED);
        assertTrue(fetchedCount > 0, "Should have fetched at least 1 document");
    }
}
```

---

## Logging

### Niveaux
- **ERROR** : Échec critique, nécessite intervention
- **WARN** : Problème non-bloquant, dégradé
- **INFO** : Progression normale des jobs
- **DEBUG** : Détails techniques

### Emojis Standardisés
```java
log.info("✅ Success: Document {} fetched", docId);
log.warn("⚠️ Warning: Retry attempt {} for {}", attempt, docId);
log.error("❌ Error: Failed to download {}", docId, exception);
log.info("🔄 Processing: OCR extraction for {}", docId);
log.info("🤖 AI Provider: Using Ollama with confidence 0.95");
log.info("📄 Document: {} articles extracted", count);
log.error("🔴 CORRUPTED: PNG disguised as PDF: {}", docId);
log.info("⏭️ Skipped: Already processed {}", docId);
```

---

## Conventions de Nommage

### Fichiers
```
PDFs : data/pdfs/{type}/{type}-{year}-{number}.pdf
       Exemple: data/pdfs/loi/loi-2024-15.pdf

OCR  : data/ocr/{type}/{type}-{year}-{number}.txt
       Exemple: data/ocr/loi/loi-2024-15.txt

JSON : data/articles/{type}/{type}-{year}-{number}.json
       Exemple: data/articles/loi/loi-2024-15.json
```

### IDs Documents
```java
// Format : {type}-{year}-{number}
String documentId = document.getDocumentId(); // "loi-2024-15"

// Méthode dans LawDocument.java
public String getDocumentId() {
    return String.format("%s-%d-%d", type, year, number);
}
```

### Jobs Spring Batch
```
Jobs  : suffixe "Job" → fetchCurrentJob, downloadJob, ocrJob
Steps : suffixe "Step" → fetchCurrentStep, downloadStep, ocrStep
```

---

## Anti-Patterns à ÉVITER

### ❌ Arrêter le Job sur Erreur

```java
// ❌ MAL - Arrête tout le job
@Override
public LawDocument process(LawDocument doc) {
    if (pdfCorrupted(doc)) {
        throw new RuntimeException("PDF corrupted");
    }
    return doc;
}

// ✅ BIEN - Continue avec statut CORRUPTED
@Override
public LawDocument process(LawDocument doc) {
    if (pdfCorrupted(doc)) {
        doc.setStatus(ProcessingStatus.CORRUPTED);
        log.warn("🔴 CORRUPTED: {}", doc.getDocumentId());
        return doc; // Job continue
    }
    return processDocument(doc);
}
```

### ❌ Traitement Non-Idempotent

```java
// ❌ MAL - Retraite toujours
@Override
public void process(LawDocument doc) {
    extractText(doc);
    doc.setStatus(ProcessingStatus.EXTRACTED);
    repository.save(doc);
}

// ✅ BIEN - Check statut
@Override
public LawDocument process(LawDocument doc) {
    if (doc.getStatus() == ProcessingStatus.EXTRACTED) {
        log.debug("⏭️ Already extracted, skipping: {}", doc.getDocumentId());
        return doc;
    }
    
    String text = extractText(doc);
    doc.setStatus(ProcessingStatus.EXTRACTED);
    return doc;
}
```

### ❌ Écraser Sans Vérification

```java
// ❌ MAL - Écrase aveuglément
public void saveJson(String docId, JsonData data) {
    Path jsonPath = fileStorageService.jsonPath("loi", docId);
    Files.writeString(jsonPath, toJson(data));
}

// ✅ BIEN - Compare confiance
public void saveJson(String docId, JsonData newData) {
    Path jsonPath = fileStorageService.jsonPath("loi", docId);
    
    if (Files.exists(jsonPath)) {
        JsonData existingData = parseJson(Files.readString(jsonPath));
        if (newData.getMetadata().getConfidence() <= existingData.getMetadata().getConfidence()) {
            log.info("⏭️ Keeping existing JSON (better confidence: {})", 
                     existingData.getMetadata().getConfidence());
            return;
        }
    }
    
    Files.writeString(jsonPath, toJson(newData));
    log.info("✅ Saved JSON with confidence {}", newData.getMetadata().getConfidence());
}
```

---

## Gestion des Erreurs Réseau

### Retry avec Backoff

```java
@Service
@Slf4j
public class LawFetchService {
    
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;
    
    public boolean checkDocumentExists(String type, int year, int number) {
        String url = buildUrl(type, year, number);
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpResponse<Void> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(30))
                        .build(),
                    HttpResponse.BodyHandlers.discarding()
                );
                
                return response.statusCode() == 200;
                
            } catch (IOException | InterruptedException e) {
                if (attempt == MAX_RETRIES) {
                    log.error("❌ Failed after {} retries: {}", MAX_RETRIES, url);
                    return false;
                }
                
                log.warn("⚠️ Retry {}/{} for {}: {}", 
                         attempt, MAX_RETRIES, url, e.getMessage());
                
                try {
                    Thread.sleep(RETRY_DELAY_MS * attempt); // Backoff exponentiel
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        
        return false;
    }
}
```

---

## Sécurité

### Secrets
```bash
# .env (NE JAMAIS COMMITER)
DATABASE_PASSWORD=secure_password
GROQ_API_KEY=gsk_xxxxxxxxxxxxx
```

```yaml
# application.yml
spring:
  datasource:
    password: ${DATABASE_PASSWORD:}

law:
  groq:
    api-key: ${GROQ_API_KEY:}
```

---

## Base de Données MySQL (Docker)

### Démarrage
```bash
# Lancer MySQL dans Docker
docker run -d \
  --name mysql-law \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=law_db \
  -p 3306:3306 \
  mysql:8.4

# Vérifier le statut
docker ps | grep mysql-law
```

### Commandes utiles
```bash
# Accéder au shell MySQL
docker exec -it mysql-law mysql -u root -proot law_db

# Nettoyer les doublons
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "DELETE t1 FROM law_documents t1 
   INNER JOIN law_documents t2 
   WHERE t1.id > t2.id 
   AND t1.type = t2.type 
   AND t1.document_year = t2.document_year 
   AND t1.number = t2.number;"

# Compter les documents par statut
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "SELECT status, COUNT(*) as count 
   FROM law_documents 
   GROUP BY status;"

# Lister les documents FETCHED
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "SELECT type, document_year, number, status 
   FROM law_documents 
   WHERE status='FETCHED' 
   LIMIT 10;"

# Backup de la base
docker exec mysql-law mysqldump -u root -proot law_db > backup.sql

# Restore depuis backup
docker exec -i mysql-law mysql -u root -proot law_db < backup.sql

# Arrêter et supprimer le conteneur
docker stop mysql-law
docker rm mysql-law
```

---

### Validation Input

```java
@Service
public class FileStorageService {
    
    public Path pdfPath(String type, String documentId) {
        // ✅ Valider input
        validateDocumentId(documentId);
        validateType(type);
        
        return basePath.resolve("pdfs")
            .resolve(type)
            .resolve(documentId + ".pdf");
    }
    
    private void validateDocumentId(String documentId) {
        if (documentId == null || documentId.contains("..") || documentId.contains("/")) {
            throw new SecurityException("Invalid document ID: " + documentId);
        }
    }
    
    private void validateType(String type) {
        if (!"loi".equals(type) && !"decret".equals(type)) {
            throw new IllegalArgumentException("Invalid type: " + type);
        }
    }
}
```

---

## Quand Créer du Nouveau Code

### ✅ Créer Nouveau
- Nouvelle fonctionnalité batch (nouveau job/step)
- Nouveau module (ex: law-search pour recherche full-text)
- Nouveau transformer PDF→JSON
- Nouveau provider IA (ex: ClaudeProvider)
- Nouveau validator/detector

### 🔄 Modifier Existant
- Bug fix
- Amélioration performance
- Ajout log/monitoring
- Refactoring Clean Code
- Enrichissement modèle existant

### ⏭️ Ne PAS Modifier
- Code généré par Lombok (@Data, @Builder, @Slf4j)
- Schémas Spring Batch (tables BATCH_*)
- Configuration Spring Boot core (auto-configuration)
- Dépendances gérées par spring-boot-starter-parent

---

## Workflow de Développement

### 1. Développer un Module

```bash
# Créer structure
mkdir -p law-mymodule/src/{main,test}/java/bj/gouv/sgg

# Créer POM
cat > law-mymodule/pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <parent>
        <groupId>bj.gouv.sgg</groupId>
        <artifactId>io.law</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>law-mymodule</artifactId>
    <dependencies>
        <dependency>law-common</dependency>
    </dependencies>
</project>
EOF

# Ajouter module au parent
# Éditer io.law/pom.xml :
# <modules>
#     ...
#     <module>law-mymodule</module>
# </modules>
```

### 2. Compiler Module

```bash
cd io.law

# Compiler un module
cd module lay-mymodule
mvn clean install

# Compiler tous les modules
mvn clean install

# Compiler sans tests
mvn clean install -DskipTests
```

### 3. Tester Module

```bash
# Tests unitaires d'un module
mvn -pl law-mymodule test

# Tests d'intégration
mvn -pl law-mymodule verify

# Test spécifique
mvn -pl law-mymodule test -Dtest=FetchProcessorTest
```

### 4. Exécuter Application

```bash
# Lancer law-api (avec tous les modules)
cd law-api
mvn spring-boot:run

# Lancer law-toJsonApp (autonome)
cd law-tojson/law-toJsonApp
mvn spring-boot:run
```

---

## Checklist Migration law.spring → io.law

### Phase 1 : law-common ✅ COMPLÉTÉ
- [x] Créer structure module
- [x] Réorganiser model/ (LawDocument avec JPA)
- [x] Implémenter repositories (LawDocumentRepository)
- [x] Conserver exception/ (21 exceptions)
- [x] Enrichir config/ (LawProperties avec directories)
- [x] Implémenter services (FileStorageService, DocumentStatusManager)
- [x] Enrichir POM avec dépendances
- [x] Compiler sans erreurs
- [x] Fix schéma DB (year → document_year pour éviter mot réservé SQL)

### Phase 2 : law-fetch ✅ COMPLÉTÉ
- [x] Créer structure module
- [x] Implémenter readers (CurrentYearReader, PreviousYearsReader)
- [x] Implémenter processor (FetchProcessor avec HEAD requests)
- [x] Implémenter writer (FetchWriter avec FetchResult + NotFoundRange)
- [x] Implémenter services (LawFetchService, NotFoundRangeService)
- [x] Créer FetchJobConfiguration (2 jobs : current + previous)
- [x] Enrichir POM avec dépendances
- [x] Compiler sans erreurs
- [x] Tests unitaires (66 tests dont 21 FetchNotFoundRange)
- [x] Tests intégration fetchCurrentJob (7 tests)
- [x] Tests fonctionnels (3/9 tests validés)
- [x] Fix force mode (bug SQL résolu)

### Phase 3 : law-download ✅ COMPLÉTÉ
- [x] Créer structure module
- [x] Implémenter reader (FetchedDocumentReader avec modes ciblé + force)
- [x] Implémenter processor (DownloadProcessor avec Apache HttpClient 5)
- [x] Implémenter writer (FileDownloadWriter + DownloadResult)
- [x] Créer model/repository (DownloadResult, DownloadResultRepository)
- [x] Implémenter service (PdfDownloadService)
- [x] Créer DownloadJobConfiguration
- [x] Enrichir POM (HttpClient 5, ByteBuddy pour tests)
- [x] Compiler sans erreurs
- [x] Tests unitaires (6 tests basiques)
- [x] Tests intégration downloadJob (8 tests avec idempotence)

### Phase 4 : law-tojson
##### 4.0 law-toJsonCommon ⏳ STRUCTURE CRÉÉE
- [x] Créer structure sous-module
- [x] Déplacer modèles partagés (Article, Signatory, DocumentMetadata, etc.)
- [x] Déplacer repositories (ArticleExtractionRepository, OcrResultRepository, etc.)
- [ ] Finaliser intégration avec autres sous-modules

#### 4.1 law-pdfToOcr
- [x] Créer structure
- [ ] Implémenter TesseractOcrService
- [ ] Implémenter ExtractionProcessor
- [ ] Implémenter ExtractionWriter
- [ ] Créer OcrJobConfiguration
- [ ] Tests

#### 4.2 law-OcrToJson
- [x] Créer structure
- [ ] Implémenter ArticleParsingService
- [ ] Implémenter ArticleExtractionProcessor
- [ ] Créer ArticleExtractionJobConfiguration
- [ ] Tests

#### 4.3 law-AIpdfToJson
- [x] Créer structure
- [ ] Implémenter IAProvider, OllamaProvider, GroqProvider
- [ ] Implémenter CapacityDetectionService
- [ ] Créer IaJobConfiguration
- [ ] Tests

#### 4.4 law-toJsonApp
- [x] Créer structure
- [ ] Créer main application
- [ ] Orchestrer 3 sous-modules (ocrJob → articleExtractionJob → iaJob)
- [ ] Configuration séquence de jobs
- [ ] Tests end-to-end

### Phase 5 : law-consolidate
- [ ] Créer structure module
- [ ] Copier ConsolidationReader
- [ ] Copier ConsolidationProcessor
- [ ] Copier ConsolidationWriter
- [ ] Copier ConsolidationService
- [ ] Créer ConsolidationJobConfiguration
- [ ] Tests

### Phase 6 : law-app (law-api renommé) ⏳ EN COURS
- [x] Créer structure module (renommé law-app)
- [x] Créer LawAppApplication (main class)
- [x] Créer JobCommandLineRunner (CLI avec support --job et --params)
- [x] Intégrer law-fetch et law-download
- [x] Build JAR exécutable (law-app-1.0-SNAPSHOT.jar)
- [x] Script tests fonctionnels (functionnal-test.sh)
- [ ] Créer controllers REST (BatchController, LawDocumentController)
- [ ] Créer services (JobLauncherService, JobMonitoringService)
- [ ] Créer FullPipelineJobConfiguration
- [ ] Créer GlobalExceptionHandler
- [ ] Créer OpenApiConfig (Swagger)
- [x] Créer application.yml avec properties complètes
- [ ] Tests REST API

### Phase 7 : Validation Globale ⏳ EN COURS
- [x] Compilation projet complet (mvn clean install ✅)
- [x] Tests intégration law-fetch (7 tests ✅)
- [x] Tests intégration law-download (8 tests ✅)
- [x] Tests unitaires (66 law-fetch + 26 law-download ✅)
- [x] Tests fonctionnels batch (3/9 tests validés : fetchCurrent full/ciblé/force)
- [ ] Compléter tests fonctionnels (fetchPrevious, downloadJob)
- [ ] Test pipeline complet (fetch → download → ocr → extract → consolidate)
- [x] Validation idempotence (tests intégration + force mode)
- [ ] Documentation Swagger
- [ ] Migration données production

---

## État Actuel du Projet (6 décembre 2025)

### ✅ Modules Complétés
1. **law-common** : Services FileStorageService + DocumentStatusManager implémentés
2. **law-fetch** : 2 jobs (current + previous) avec 66 tests unitaires + 7 intégration
3. **law-download** : 1 job avec 26 tests (8 intégration + 18 unitaires)

### 📊 Statistiques Tests
- **Tests unitaires** : 92 tests (66 law-fetch + 26 law-download)
- **Tests intégration** : 15 tests (7 law-fetch + 8 law-download)
- **Tests fonctionnels** : 3/9 validés (fetchCurrentJob full/ciblé/force)
- **Couverture** : Idempotence, force mode, retry, error handling

### 🐛 Bugs Résolus
- Fix SQL : `year` → `document_year` (mot réservé MySQL)
- Fix force mode : Duplicate column issue dans `fetch_results`
- Build Maven : Configuration flatten-plugin pour `${revision}`

### 🚀 Prochaines Étapes
1. **Compléter tests fonctionnels** : fetchPreviousJob (3 tests), downloadJob (3 tests)
2. **Implémenter law-tojson** : 4 sous-modules (OCR, parsing, IA, orchestration)
3. **Implémenter law-consolidate** : Import JSON → MySQL
4. **Finaliser law-app** : API REST + Swagger + monitoring
5. **Pipeline complet** : fetch → download → ocr → extract → consolidate

### 📁 Fichiers Non Commités
- 7 modifiés (`.gitignore`, `functionnal-test.sh`, modèles avec annotations JPA)
- 38 nouveaux (services, tests, configurations)
- 12 supprimés (entités obsolètes déplacées vers sous-modules)

---

## Rappel Final

**Toujours privilégier** :
1. ✅ **Résilience** : Job continue malgré erreurs individuelles
2. ✅ **Idempotence** : Re-run safe, pas de duplication (validé par tests)
3. ✅ **Clean Code** : Exceptions spécifiques, pas de null, try-with-resources
4. ✅ **Modularité** : Découpage clair, dépendances minimales
5. ✅ **Testabilité** : Tests unitaires + intégration pour chaque module

**Migration progressive** :
- ✅ law-common → ✅ law-fetch → ✅ law-download → ⏳ law-tojson → ⏳ law-consolidate → ⏳ law-app
- Compilation + tests avant module suivant
- 1 module à la fois

**Objectif** : Architecture propre, maintenable, évolutive ✨
