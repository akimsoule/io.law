# Plan de Migration vers Spring Batch - Modules io.law

## 📊 État Actuel

### ❌ AUCUN Module n'utilise Spring Batch

**Tous les modules** utilisent actuellement un **pattern manuel Reader-Processor-Writer** sans Spring Batch :

1. **law-fetch** ❌
   - `FetchCurrentServiceImpl`, `FetchPreviousServiceImpl`
   - Pattern manuel avec méthodes séparées
   - Pas de configuration Spring Batch

2. **law-download** ❌
   - `DownloadServiceImpl`
   - Pattern Reader-Processor-Writer manuel
   - Commentaires "READER", "PROCESSOR", "WRITER" dans le code

3. **law-pdf-ocr** ❌
   - `OcrProcessingServiceImpl`
   - Pattern Reader-Processor-Writer manuel
   - Sauvegarde batch avec `saveAll()`

4. **law-ocr-json** ❌
   - `ArticleExtractionServiceImpl`
   - Pattern manuel

5. **law-ai** ❌
   - `IAServiceImpl`
   - Pattern manuel

6. **law-consolidate** ❌
   - `ConsolidationServiceImpl`
   - Pattern manuel

7. **law-qa** ❌
   - Services d'analyse/rapport
   - Pas besoin de batch processing

### 📦 Dépendances Actuelles

Aucune dépendance Spring Batch dans les POM :
- ❌ `spring-boot-starter-batch`
- ❌ `spring-batch-core`
- ❌ JobRepository, StepBuilder, etc.

---

## 🎯 Objectifs de la Migration

### Pourquoi migrer vers Spring Batch ?

1. **Gestion des Transactions** : Automatique par chunk
2. **Resilience** : Skip/Retry intégré
3. **Monitoring** : JobRepository avec historique
4. **Restart** : Reprise automatique en cas d'échec
5. **Performance** : Parallélisation native
6. **Standards** : Pattern industriel reconnu
7. **Testabilité** : Composants isolés testables
8. **Monitoring** : Métriques et dashboard Spring Boot Admin

### ⚠️ Contraintes Déploiement Raspberry Pi

**Environnement cible** : Raspberry Pi (2-8 GB RAM max)

**Adaptations nécessaires** :

1. **Chunk Size Réduit** 🔴
   - Chunk size = **10-50** au lieu de 100-1000
   - Éviter OutOfMemoryError sur traitement volumineux
   - Commit fréquent pour libérer mémoire

2. **Configuration JVM Optimisée** 🔴
   ```bash
   java -Xms256m -Xmx1024m -XX:+UseSerialGC -jar law-app.jar
   ```
   - Heap max : 1 GB (laisse RAM pour système/OCR)
   - Serial GC (moins gourmand)
   - Pas de parallélisation excessive

3. **Tesseract OCR Optimisé** 🔴 **CRITIQUE**
   - **Traiter 1 PDF à la fois** (chunk size = **1** impératif)
   - **Libération mémoire forcée** après chaque traitement
   - **Réduction DPI** : 150-200 DPI max (au lieu de 300)
   - **Tesseract threads** : 1 seul thread (`OMP_THREAD_LIMIT=1`)
   - **Garbage Collection** : Forcer GC après chaque PDF lourd
   - **Monitoring heap** : Alertes si > 800 MB
   
   ```java
   // Configuration OCR Processor pour Raspi
   @Component
   public class OcrProcessor implements ItemProcessor<File, OcrResult> {
       
       @Override
       public OcrResult process(File pdfFile) {
           try {
               // Vérifier mémoire disponible AVANT traitement
               Runtime runtime = Runtime.getRuntime();
               long freeMemory = runtime.freeMemory();
               if (freeMemory < 200_000_000) { // < 200 MB
                   log.warn("⚠️ Mémoire faible: {} MB", freeMemory / 1_000_000);
                   System.gc(); // Forcer GC
                   Thread.sleep(1000);
               }
               
               // OCR avec paramètres allégés
               TesseractOCR ocr = new TesseractOCR();
               ocr.setThreads(1);              // 1 seul thread
               ocr.setDPI(150);                // DPI réduit
               ocr.setPageSegMode(1);          // Auto
               String text = ocr.doOCR(pdfFile);
               
               // Sauvegarder résultat
               saveOcrResult(text, documentId);
               
               return new OcrResult(entity, true, null);
               
           } finally {
               // OBLIGATOIRE: Libération mémoire
               ocr = null;
               System.gc();
               log.debug("🧹 Mémoire libérée après OCR");
           }
       }
   }
   ```

4. **Base de Données Légère** 🟡
   - **Option 1** : MySQL avec `innodb_buffer_pool_size=128M`
   - **Option 2** : H2 ou SQLite (plus léger, suffisant pour metadata)
   - JobRepository Spring Batch en base séparée

5. **Démarrage Rapide Application** 🔴 **CRITIQUE**
   - **Lazy Initialization** : Beans chargés uniquement à la demande
   - **Component Index** : Index Maven pour éviter scan complet
   - **Désactivation fonctionnalités** : DevTools, JMX, Actuator (prod)
   - **Autoconfiguration sélective** : Exclure configurations inutiles
   - **Startup optimisé** : Target < 10 secondes sur Raspi
   
   ```properties
   # application.properties - Démarrage rapide Raspi
   
   # Lazy initialization (charge beans à la demande)
   spring.main.lazy-initialization=true
   
   # Désactiver fonctionnalités non critiques
   spring.jmx.enabled=false
   spring.devtools.restart.enabled=false
   management.endpoints.enabled-by-default=false
   management.endpoint.health.enabled=true  # Garder health check uniquement
   
   # Autoconfiguration sélective
   spring.autoconfigure.exclude=\
     org.springframework.boot.autoconfigure.admin.SpringApplicationAdminJmxAutoConfiguration,\
     org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration,\
     org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration,\
     org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration
   
   # Jackson optimization
   spring.jackson.serialization.indent_output=false
   
   # Logging rapide
   logging.pattern.console=%d{HH:mm:ss.SSS} [%thread] %-5level %logger{20} - %msg%n
   
   # Hibernate lazy
   spring.jpa.properties.hibernate.enable_lazy_load_no_trans=false
   spring.jpa.open-in-view=false
   
   # Connection pool minimal au démarrage
   spring.datasource.hikari.minimum-idle=1
   spring.datasource.hikari.initialization-fail-timeout=-1
   ```
   
   ```java
   // Application.java - Configuration startup optimisé
   @SpringBootApplication(
       scanBasePackages = "bj.gouv.sgg",  // Scan ciblé
       exclude = {
           // Exclure autoconfigs inutiles
           DataSourceAutoConfiguration.class,  // Configuration manuelle
           HibernateJpaAutoConfiguration.class
       }
   )
   @EnableBatchProcessing(modular = true)  // Batch processing modulaire
   public class LawApplication {
       
       public static void main(String[] args) {
           SpringApplication app = new SpringApplication(LawApplication.class);
           
           // 🍓 Raspi: Optimisations démarrage
           app.setLazyInitialization(true);
           app.setLogStartupInfo(true);
           app.setRegisterShutdownHook(true);
           
           // Headless mode
           System.setProperty("java.awt.headless", "true");
           
           long startTime = System.currentTimeMillis();
           app.run(args);
           long duration = System.currentTimeMillis() - startTime;
           
           log.info("🍓 Application démarrée en {}ms", duration);
       }
   }
   ```
   
   ```xml
   <!-- pom.xml - Générer index des composants -->
   <plugin>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-maven-plugin</artifactId>
       <configuration>
           <createIndex>true</createIndex>  <!-- Accélère component scanning -->
       </configuration>
   </plugin>
   ```

6. **Pas de Parallélisation** 🔴
   ```java
   // ❌ PAS ça sur Raspi
   .taskExecutor(new SimpleAsyncTaskExecutor())
   .throttleLimit(4)
   
   // ✅ Séquentiel
   .taskExecutor(new SyncTaskExecutor())
   ```

7. **Pagination Stricte** 🔴
   - Reader avec pagination : max 50 items par page
   - Ne jamais charger toute la table en mémoire
   - Stream/Iterator pattern pour gros volumes

8. **Monitoring Mémoire** 🟡
   - Activer Spring Boot Actuator
   - Endpoint `/actuator/metrics/jvm.memory.used`
   - Alertes si heap > 80%

9. **Swap si Nécessaire** 🟡
   ```bash
   # Sur Raspi : activer swap 2GB minimum
   sudo dphys-swapfile swapoff
   sudo nano /etc/dphys-swapfile  # CONF_SWAPSIZE=2048
   sudo dphys-swapfile setup
   sudo dphys-swapfile swapon
   ```

---

## 🚀 Stratégie de Migration Globale

### Approche Recommandée : **Migration Progressive Module par Module**

**Principe** : Ne pas casser l'existant, créer en parallèle, puis basculer

#### Étape 1 : Ajout Spring Batch au Projet (1 jour)
1. Ajouter dépendances Spring Batch dans parent POM
2. Créer configuration Spring Batch globale
3. Configurer JobRepository (MySQL)
4. Tester configuration de base

#### Étape 2 : Migration Modules (2-3 jours/module)
Ordre de migration (du socle vers les modules métier) :

1. **law-common** (PRIORITÉ 1 - Infrastructure partagée) ⚡
   - Module socle utilisé par TOUS les autres
   - Contient entités, repositories, services communs
   - Migration vers Spring Data JPA + Spring Context
   - Une fois migré, tous les modules peuvent utiliser l'infrastructure Spring

2. **law-fetch** (point d'entrée du pipeline métier)
3. **law-download** (téléchargement PDFs)
4. **law-pdf-ocr** (traitement OCR)
5. **law-ocr-json** (extraction articles)
6. **law-consolidate** (consolidation en BD)
7. **law-ai** (optionnel, amélioration IA)

#### Étape 3 : Orchestration Complète (1-2 jours)
- Job orchestrateur qui enchaîne tous les steps
- Configuration des dépendances entre steps
- Tests end-to-end

---

## 🎯 Ordre de Migration Détaillé

### Phase 1 : law-common (Infrastructure) - 2-3 jours ⚡
- Migration vers Spring Boot + Spring Data JPA
- Suppression singletons manuels (`getInstance()`)
- Injection de dépendances (@Autowired, @Component)
- Spring Data JPA au lieu d'EntityManager manuel
- **Tous les modules en bénéficient ensuite**

### Phase 2 : law-fetch (Collecte) - 2 jours
- Spring Batch pour fetchCurrentJob et fetchPreviousJob
- Dépend de law-common (déjà migré)

### Phase 3 : law-download (Téléchargement) - 2 jours
- Spring Batch pour downloadJob
- Dépend de law-common + law-fetch

### Phase 4 : law-pdf-ocr (OCR) - 2-3 jours
- Spring Batch pour ocrJob
- Dépend de law-common + law-download

### Phase 5 : law-ocr-json (Extraction) - 2-3 jours
- Spring Batch pour extractionJob
- Dépend de law-common + law-pdf-ocr

### Phase 6 : law-consolidate (Consolidation) - 2 jours
- Spring Batch pour consolidationJob
- Dépend de law-common + law-ocr-json

### Phase 7 : law-ai (IA Enhancement) - 2 jours
- Spring Batch pour aiJob (optionnel)
- Dépend de law-common

### Phase 8 : Orchestration Globale - 1-2 jours
- Job orchestrateur enchaînant tous les steps
- Tests end-to-end complets

**Durée totale estimée : 15-20 jours**

---

## 📋 PHASE 1 : Migration law-common (Infrastructure Socle)

### Pourquoi commencer par law-common ?

**law-common est le module FONDAMENTAL** :
- ✅ Utilisé par TOUS les modules (fetch, download, ocr, extraction, etc.)
- ✅ Contient les entités JPA (`LawDocumentEntity`, etc.)
- ✅ Contient les repositories (`JpaLawDocumentRepository`, etc.)
- ✅ Contient les services partagés (`LawDocumentValidator`, `AppConfig`, etc.)
- ✅ Une fois migré vers Spring, tous les modules peuvent utiliser DI et Spring Data JPA

**Bénéfices** :
- Infrastructure Spring disponible pour tous
- Suppression des singletons manuels
- Injection de dépendances partout
- Spring Data JPA au lieu de EntityManager manuel
- Base solide pour migrer les autres modules

---

## 📋 Phase 1.1 : Parent POM - Ajouter Dépendances

### Module 1: law-pdf-ocr → Spring Batch

**État actuel**:
```java
public class OcrProcessingServiceImpl {
    // READER: readPdfFiles() - Liste PDFs du disque
    // PROCESSOR: processPdfFile() - Effectue OCR
    // WRITER: writeOcrResults() - Sauvegarde entités avec saveAll()
}
```

**Architecture cible**:

#### 1.1. Créer le Package Batch
```
law-pdf-ocr/
├── src/main/java/bj/gouv/sgg/
│   ├── batch/
│   │   ├── reader/
│   │   │   └── PdfFileItemReader.java
│   │   ├── processor/
│   │   │   └── OcrProcessor.java
│   │   └── writer/
│   │       └── OcrResultWriter.java
│   ├── config/
│   │   └── OcrJobConfiguration.java
│   └── service/
│       └── OcrService.java (existant, conservé)
```

#### 1.2. PdfFileItemReader
```java
@Component
@StepScope
public class PdfFileItemReader implements ItemReader<File> {
    
    private final AppConfig config;
    private final LawDocumentValidator validator;
    private final LawDocumentService lawDocumentService;
    
    @Value("#{jobParameters['type']}")
    private String type;
    
    private List<File> pdfFiles;
    private int currentIndex = 0;
    
    @PostConstruct
    public void init() {
        Path pdfDir = config.getStoragePath().resolve("pdfs").resolve(type);
        
        // Lire tous les PDFs
        pdfFiles = Files.walk(pdfDir, 1)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".pdf"))
            .map(Path::toFile)
            .filter(this::shouldProcess)
            .collect(Collectors.toList());
            
        log.info("📖 READER: {} PDFs à traiter", pdfFiles.size());
    }
    
    private boolean shouldProcess(File pdfFile) {
        String documentId = pdfFile.getName().replace(".pdf", "");
        Optional<LawDocumentEntity> entityOpt = lawDocumentService.findByDocumentId(documentId);
        
        if (entityOpt.isEmpty()) {
            return true; // Nouveau document
        }
        
        return validator.mustOcr(entityOpt.get());
    }
    
    @Override
    public File read() {
        if (currentIndex < pdfFiles.size()) {
            return pdfFiles.get(currentIndex++);
        }
        return null; // Fin du stream
    }
}
```

#### 1.3. OcrProcessor
```java
@Component
@StepScope
public class OcrProcessor implements ItemProcessor<File, OcrResult> {
    
    private final OcrService ocrService;
    private final LawDocumentService lawDocumentService;
    private final AppConfig config;
    
    @Value("#{jobParameters['type']}")
    private String type;
    
    @Override
    public OcrResult process(File pdfFile) {
        String documentId = pdfFile.getName().replace(".pdf", "");
        
        try {
            Path ocrFile = config.getStoragePath()
                .resolve("ocr")
                .resolve(type)
                .resolve(documentId + ".txt");
            
            Files.createDirectories(ocrFile.getParent());
            
            // Effectuer OCR
            ocrService.performOcr(pdfFile, ocrFile.toFile());
            
            // Récupérer/créer entité
            LawDocumentEntity entity = lawDocumentService.findByDocumentId(documentId)
                .orElseGet(() -> LawDocumentEntity.createFromDocumentId(documentId, type));
            
            entity.setStatus(ProcessingStatus.OCRED);
            entity.setOcrPath(ocrFile.toString());
            entity.setErrorMessage(null);
            
            log.debug("✅ OCR réussi: {}", documentId);
            return new OcrResult(entity, true, null);
            
        } catch (CorruptedPdfException e) {
            LawDocumentEntity entity = lawDocumentService.findByDocumentId(documentId)
                .orElseGet(() -> LawDocumentEntity.createFromDocumentId(documentId, type));
            
            entity.setStatus(ProcessingStatus.FAILED_CORRUPTED);
            entity.setErrorMessage("PDF corrompu: " + e.getMessage());
            
            log.error("❌ PDF corrompu: {}", documentId);
            return new OcrResult(entity, false, "CORRUPTED");
            
        } catch (Exception e) {
            LawDocumentEntity entity = lawDocumentService.findByDocumentId(documentId)
                .orElseGet(() -> LawDocumentEntity.createFromDocumentId(documentId, type));
            
            entity.setStatus(ProcessingStatus.FAILED_OCR);
            entity.setErrorMessage(e.getMessage());
            
            log.error("❌ Échec OCR: {}", documentId, e);
            return new OcrResult(entity, false, "ERROR");
        }
    }
    
    public record OcrResult(LawDocumentEntity entity, boolean success, String errorType) {}
}
```

#### 1.4. OcrResultWriter
```java
@Component
public class OcrResultWriter implements ItemWriter<OcrResult> {
    
    private final LawDocumentService lawDocumentService;
    
    @Override
    public void write(Chunk<? extends OcrResult> chunk) {
        List<LawDocumentEntity> entities = chunk.getItems().stream()
            .map(OcrResult::entity)
            .toList();
        
        if (entities.isEmpty()) {
            log.info("💾 WRITER: Aucune entité à sauvegarder");
            return;
        }
        
        log.info("💾 WRITER: Sauvegarde de {} entités...", entities.size());
        lawDocumentService.saveAll(entities);
        
        long success = chunk.getItems().stream().filter(OcrResult::success).count();
        long failed = entities.size() - success;
        
        log.info("💾 WRITER: ✅ {} succès, ❌ {} échecs", success, failed);
    }
}
```

#### 1.5. OcrJobConfiguration
```java
@Configuration
@RequiredArgsConstructor
@Slf4j
public class OcrJobConfiguration {
    
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    
    @Bean
    public Job ocrJob(Step ocrStep) {
        return new JobBuilder("ocrJob", jobRepository)
            .start(ocrStep)
            .build();
    }
    
    @Bean
    public Step ocrStep(PdfFileItemReader reader,
                        OcrProcessor processor,
                        OcrResultWriter writer) {
        return new StepBuilder("ocrStep", jobRepository)
            .<File, OcrResult>chunk(1, transactionManager)  // 🍓 Raspi: chunk=1 OBLIGATOIRE (OCR très gourmand)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .skip(Exception.class)
            .skipLimit(Integer.MAX_VALUE)
            .listener(new StepExecutionListener() {
                @Override
                public void beforeStep(StepExecution stepExecution) {
                    String type = stepExecution.getJobParameters().getString("type");
                    log.info("🎯 OCR Job: type={}", type);
                    
                    // 🍓 Raspi: Vérifier mémoire disponible AVANT traitement
                    Runtime runtime = Runtime.getRuntime();
                    long totalMemory = runtime.totalMemory() / 1_000_000;
                    long freeMemory = runtime.freeMemory() / 1_000_000;
                    long maxMemory = runtime.maxMemory() / 1_000_000;
                    
                    log.info("🍓 Raspi Memory: Total={}MB, Free={}MB, Max={}MB", 
                             totalMemory, freeMemory, maxMemory);
                    
                    if (freeMemory < 200) {
                        log.warn("⚠️ Mémoire faible! Forcer GC avant OCR...");
                        System.gc();
                        try { Thread.sleep(2000); } catch (Exception e) {}
                    }
                }
                
                @Override
                public ExitStatus afterStep(StepExecution stepExecution) {
                    // 🍓 Raspi: Libération mémoire APRÈS traitement
                    log.info("🧹 Nettoyage mémoire post-OCR...");
                    System.gc();
                    
                    Runtime runtime = Runtime.getRuntime();
                    long freeMemory = runtime.freeMemory() / 1_000_000;
                    log.info("🍓 Mémoire disponible après OCR: {} MB", freeMemory);
                    
                    return StepExecutionListener.super.afterStep(stepExecution);
                }
            })
            .build();
    }
}
```

**🍓 Points critiques OCR pour Raspberry Pi** :
- ✅ Chunk size = **1** (UN SEUL PDF à la fois)
- ✅ Vérification mémoire avant/après chaque step
- ✅ GC forcé si mémoire < 200 MB
- ✅ Monitoring heap avec StepExecutionListener
- ✅ Thread.sleep() pour laisser le GC s'exécuter
- ✅ Skip illimité pour ne pas bloquer sur erreur

---
            .incrementer(new RunIdIncrementer())
            .start(ocrStep)
            .build();
    }
    
    @Bean
    public Step ocrStep(PdfFileItemReader reader,
                        OcrProcessor processor,
                        OcrResultWriter writer) {
        return new StepBuilder("ocrStep", jobRepository)
            .<File, OcrResult>chunk(10, transactionManager)  // 🍓 Raspi: chunk=10 max (OCR gourmand)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .skip(Exception.class)
            .skipLimit(Integer.MAX_VALUE)
            .listener(new StepExecutionListener() {
                @Override
                public void beforeStep(StepExecution stepExecution) {
                    String type = stepExecution.getJobParameters().getString("type");
                    log.info("🎯 OCR Job: type={}", type);
                }
            })
            .build();
    }
}
```

---

### Module 2: law-ocr-json → Spring Batch

**État actuel**:
```java
public class ArticleExtractionServiceImpl {
    // Pattern manuel: read OCR files, extract articles, save JSON
}
```

**Architecture cible**:

#### 2.1. Structure
```
law-ocr-json/
├── src/main/java/bj/gouv/sgg/
│   ├── batch/
│   │   ├── reader/
│   │   │   └── OcrFileItemReader.java
│   │   ├── processor/
│   │   │   └── ArticleExtractionProcessor.java
│   │   └── writer/
│   │       └── ArticleJsonWriter.java
│   └── config/
│       └── ExtractionJobConfiguration.java
```

#### 2.2. OcrFileItemReader
```java
@Component
@StepScope
public class OcrFileItemReader implements ItemReader<OcrDocument> {
    
    private final AppConfig config;
    private final LawDocumentValidator validator;
    private final LawDocumentService lawDocumentService;
    
    @Value("#{jobParameters['type']}")
    private String type;
    
    private List<OcrDocument> ocrDocuments;
    private int currentIndex = 0;
    
    @PostConstruct
    public void init() {
        // Récupérer documents avec status OCRED
        List<LawDocumentEntity> entities = lawDocumentService.findByTypeAndStatus(type, ProcessingStatus.OCRED);
        
        // Filtrer avec validator pour vérifier fichiers
        ocrDocuments = entities.stream()
            .filter(validator::mustExtractArticles)
            .map(this::loadOcrDocument)
            .filter(Objects::nonNull)
            .toList();
            
        log.info("📖 READER: {} documents OCR à extraire", ocrDocuments.size());
    }
    
    private OcrDocument loadOcrDocument(LawDocumentEntity entity) {
        try {
            Path ocrPath = validator.getOcrPath(entity);
            String ocrText = Files.readString(ocrPath);
            return new OcrDocument(entity, ocrText);
        } catch (Exception e) {
            log.error("❌ Impossible de lire OCR: {}", entity.getDocumentId(), e);
            return null;
        }
    }
    
    @Override
    public OcrDocument read() {
        if (currentIndex < ocrDocuments.size()) {
            return ocrDocuments.get(currentIndex++);
        }
        return null;
    }
    
    public record OcrDocument(LawDocumentEntity entity, String ocrText) {}
}
```

#### 2.3. ArticleExtractionProcessor
```java
@Component
@StepScope
public class ArticleExtractionProcessor implements ItemProcessor<OcrDocument, ExtractionResult> {
    
    private final ArticleExtractor extractor;
    
    @Override
    public ExtractionResult process(OcrDocument ocrDoc) {
        LawDocumentEntity entity = ocrDoc.entity();
        
        try {
            // Extraire articles
            List<Article> articles = extractor.extractArticles(ocrDoc.ocrText(), entity);
            
            entity.setStatus(ProcessingStatus.EXTRACTED);
            entity.setErrorMessage(null);
            
            log.debug("✅ Extraction réussie: {} ({} articles)", 
                     entity.getDocumentId(), articles.size());
            
            return new ExtractionResult(entity, articles, true, null);
            
        } catch (Exception e) {
            entity.setStatus(ProcessingStatus.FAILED_EXTRACTION);
            entity.setErrorMessage(e.getMessage());
            
            log.error("❌ Échec extraction: {}", entity.getDocumentId(), e);
            return new ExtractionResult(entity, List.of(), false, e.getMessage());
        }
    }
    
    public record ExtractionResult(
        LawDocumentEntity entity,
        List<Article> articles,
        boolean success,
        String error
    ) {}
}
```

#### 2.4. ArticleJsonWriter
```java
@Component
public class ArticleJsonWriter implements ItemWriter<ExtractionResult> {
    
    private final LawDocumentService lawDocumentService;
    private final AppConfig config;
    private final ObjectMapper objectMapper;
    
    @Override
    public void write(Chunk<? extends ExtractionResult> chunk) {
        List<LawDocumentEntity> entities = new ArrayList<>();
        
        for (ExtractionResult result : chunk.getItems()) {
            if (result.success()) {
                // Sauvegarder JSON
                try {
                    Path jsonPath = config.getStoragePath()
                        .resolve("articles")
                        .resolve(result.entity().getType())
                        .resolve(result.entity().getDocumentId() + ".json");
                    
                    Files.createDirectories(jsonPath.getParent());
                    
                    objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValue(jsonPath.toFile(), result.articles());
                    
                    result.entity().setJsonPath(jsonPath.toString());
                    
                } catch (Exception e) {
                    log.error("❌ Échec sauvegarde JSON: {}", 
                             result.entity().getDocumentId(), e);
                    result.entity().setStatus(ProcessingStatus.FAILED_EXTRACTION);
                    result.entity().setErrorMessage("Échec sauvegarde JSON: " + e.getMessage());
                }
            }
            
            entities.add(result.entity());
        }
        
        lawDocumentService.saveAll(entities);
        
        long success = chunk.getItems().stream().filter(ExtractionResult::success).count();
        log.info("💾 WRITER: ✅ {} succès, ❌ {} échecs", success, entities.size() - success);
    }
}
```

---

### Module 3: law-ai → Spring Batch

**État actuel**:
```java
public class IAServiceImpl {
    // Transformation avec IA (Ollama/Groq)
}
```

**Architecture cible**:

#### 3.1. Structure
```
law-ai/
├── src/main/java/bj/gouv/sgg/
│   ├── batch/
│   │   ├── reader/
│   │   │   └── ExtractedDocumentReader.java
│   │   ├── processor/
│   │   │   └── AIEnhancementProcessor.java
│   │   └── writer/
│   │       └── EnhancedArticleWriter.java
│   └── config/
│       └── AIJobConfiguration.java
```

#### 3.2. ExtractedDocumentReader
```java
@Component
@StepScope
public class ExtractedDocumentReader implements ItemReader<ExtractedDocument> {
    
    private final LawDocumentService lawDocumentService;
    private final LawDocumentValidator validator;
    
    @Value("#{jobParameters['type']}")
    private String type;
    
    @Value("#{jobParameters['aiMode']}")
    private String aiMode; // "ollama" ou "groq"
    
    private List<ExtractedDocument> documents;
    private int currentIndex = 0;
    
    @PostConstruct
    public void init() {
        // Documents EXTRACTED qui n'ont pas encore été traités par IA
        List<LawDocumentEntity> entities = lawDocumentService
            .findByTypeAndStatus(type, ProcessingStatus.EXTRACTED);
        
        documents = entities.stream()
            .filter(entity -> validator.isExtracted(entity))
            .map(this::loadExtractedDocument)
            .filter(Objects::nonNull)
            .toList();
            
        log.info("📖 READER: {} documents pour traitement IA ({})", 
                documents.size(), aiMode);
    }
    
    private ExtractedDocument loadExtractedDocument(LawDocumentEntity entity) {
        try {
            Path jsonPath = validator.getJsonPath(entity);
            List<Article> articles = objectMapper.readValue(
                jsonPath.toFile(),
                new TypeReference<List<Article>>() {}
            );
            return new ExtractedDocument(entity, articles);
        } catch (Exception e) {
            log.error("❌ Impossible de charger JSON: {}", entity.getDocumentId(), e);
            return null;
        }
    }
    
    @Override
    public ExtractedDocument read() {
        if (currentIndex < documents.size()) {
            return documents.get(currentIndex++);
        }
        return null;
    }
    
    public record ExtractedDocument(LawDocumentEntity entity, List<Article> articles) {}
}
```

#### 3.3. AIEnhancementProcessor
```java
@Component
@StepScope
public class AIEnhancementProcessor implements ItemProcessor<ExtractedDocument, AIResult> {
    
    private final IAService iaService; // Service existant
    
    @Value("#{jobParameters['aiMode']}")
    private String aiMode;
    
    @Override
    public AIResult process(ExtractedDocument doc) {
        LawDocumentEntity entity = doc.entity();
        
        try {
            // Amélioration IA article par article
            List<Article> enhancedArticles = new ArrayList<>();
            
            for (Article article : doc.articles()) {
                Article enhanced = iaService.enhanceArticle(article, aiMode);
                enhancedArticles.add(enhanced);
            }
            
            // Pas de changement de status (reste EXTRACTED)
            // Juste mise à jour du JSON avec contenu amélioré
            
            log.debug("✅ IA Enhancement: {} ({} articles)", 
                     entity.getDocumentId(), enhancedArticles.size());
            
            return new AIResult(entity, enhancedArticles, true, null);
            
        } catch (Exception e) {
            log.error("❌ Échec IA: {}", entity.getDocumentId(), e);
            return new AIResult(entity, doc.articles(), false, e.getMessage());
        }
    }
    
    public record AIResult(
        LawDocumentEntity entity,
        List<Article> articles,
        boolean success,
        String error
    ) {}
}
```

---

### Module 4: law-qa → Spring Batch (OPTIONNEL)

Les services de QA (OcrQualityServiceImpl, JsonQualityServiceImpl) sont des outils d'analyse/rapport.
Ils n'ont pas besoin de Spring Batch car ce n'est pas du traitement de masse.

**Recommandation**: Garder le pattern actuel (services simples).

---

## 📅 Ordre de Migration Recommandé

### Phase 1: OCR (1-2 jours)
- ✅ Créer structure batch pour law-pdf-ocr
- ✅ Implémenter Reader, Processor, Writer
- ✅ Configuration Job
- ✅ Tests unitaires
- ✅ Test d'intégration

### Phase 2: Extraction Articles (1-2 jours)
- ✅ Créer structure batch pour law-ocr-json
- ✅ Implémenter Reader, Processor, Writer
- ✅ Configuration Job
- ✅ Tests
- ✅ Intégration avec pipeline

### Phase 3: AI Enhancement (2-3 jours)
- ✅ Créer structure batch pour law-ai
- ✅ Implémenter Reader, Processor, Writer
- ✅ Configuration Job avec paramètre aiMode
- ✅ Tests
- ✅ Intégration optionnelle dans pipeline

### Phase 4: Orchestration Complète (1 jour)
- ✅ Mettre à jour FullJobConfiguration
- ✅ Intégrer tous les nouveaux steps
- ✅ Tests end-to-end
- ✅ Documentation

**Durée totale estimée**: 5-8 jours

---

## ✅ Checklist Migration par Module

### law-pdf-ocr
- [ ] Créer package `batch.reader`
- [ ] Créer package `batch.processor`
- [ ] Créer package `batch.writer`
- [ ] Implémenter `PdfFileItemReader`
- [ ] Implémenter `OcrProcessor`
- [ ] Implémenter `OcrResultWriter`
- [ ] Créer `OcrJobConfiguration`
- [ ] Supprimer `OcrProcessingServiceImpl` (ou marquer @Deprecated)
- [ ] Tests unitaires Reader/Processor/Writer
- [ ] Test d'intégration Job complet
- [ ] Mettre à jour documentation

### law-ocr-json
- [ ] Créer package `batch.reader`
- [ ] Créer package `batch.processor`
- [ ] Créer package `batch.writer`
- [ ] Implémenter `OcrFileItemReader`
- [ ] Implémenter `ArticleExtractionProcessor`
- [ ] Implémenter `ArticleJsonWriter`
- [ ] Créer `ExtractionJobConfiguration`
- [ ] Supprimer `ArticleExtractionServiceImpl` (ou marquer @Deprecated)
- [ ] Tests
- [ ] Documentation

### law-ai
- [ ] Créer package `batch.reader`
- [ ] Créer package `batch.processor`
- [ ] Créer package `batch.writer`
- [ ] Implémenter `ExtractedDocumentReader`
- [ ] Implémenter `AIEnhancementProcessor`
- [ ] Implémenter `EnhancedArticleWriter`
- [ ] Créer `AIJobConfiguration`
- [ ] Tests
- [ ] Documentation

---

## 🎯 Bénéfices de la Migration

### 1. Cohérence Architecture
- ✅ Tous les modules utilisent Spring Batch
- ✅ Pattern uniforme Reader-Processor-Writer
- ✅ Gestion d'erreurs standardisée

### 2. Fonctionnalités Spring Batch
- ✅ Gestion automatique des transactions
- ✅ Chunk processing configurable
- ✅ Skip/Retry automatique
- ✅ Job Parameters dynamiques
- ✅ Monitoring via JobRepository
- ✅ Restart capabilities

### 3. Performance
- ✅ Chunk processing optimisé
- ✅ Possibilité de parallélisation (taskExecutor) - ⚠️ Désactivé sur Raspi (RAM limitée)
- ✅ Gestion mémoire améliorée

### 4. Maintenabilité
- ✅ Code plus simple et lisible
- ✅ Séparation des responsabilités claire
- ✅ Tests plus faciles (Reader/Processor/Writer séparés)
- ✅ Réutilisabilité des composants

---

## 🔧 Configuration Globale

### application.yml
```yaml
spring:
  batch:
    job:
      enabled: false  # Pas de lancement automatique
    jdbc:
      initialize-schema: always
  
  # 🍓 Raspi: Optimisations démarrage rapide
  main:
    lazy-initialization: true  # Beans chargés à la demande
    banner-mode: off            # Pas de banner ASCII (gagne 50-100ms)
  
  jmx:
    enabled: false              # Désactiver JMX
  
  jpa:
    open-in-view: false         # Pas de session Hibernate dans view
    properties:
      hibernate:
        enable_lazy_load_no_trans: false
        jdbc:
          batch_size: 20
          
  datasource:
    hikari:
      minimum-idle: 1           # 1 seule connection au démarrage
      maximum-pool-size: 5      # Pool réduit
      initialization-fail-timeout: -1
      
management:
  endpoints:
    enabled-by-default: false   # Désactiver tous les endpoints
    web:
      exposure:
        include: health          # Garder health uniquement
  endpoint:
    health:
      enabled: true
      
law:
  batch:
    chunk-size: 10  # Taille des chunks pour tous les jobs
    thread-pool-size: 1  # 🍓 Raspi: Pas de parallélisation
  storage:
    base-path: ./data
  startup:
    optimization: true  # Flag pour optimisations Raspi
```

### Dépendances Maven (parent POM)
```xml
<dependencies>
    <!-- Spring Boot Starter (minimal) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
        <exclusions>
            <!-- 🍓 Raspi: Exclure dépendances lourdes non utilisées -->
            <exclusion>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-logging</artifactId>
            </exclusion>
        </exclusions>
    </dependency>
    
    <!-- Logback léger (à la place de logging) -->
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
    </dependency>
    
    <!-- Spring Batch -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-batch</artifactId>
    </dependency>
    
    <!-- Spring Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    
    <!-- MySQL Driver -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <!-- 🍓 Raspi: Générer index composants (accélère démarrage) -->
                <createIndex>true</createIndex>
                <!-- Layout optimisé -->
                <layout>JAR</layout>
                <!-- Exclure DevTools de prod -->
                <excludeDevtools>true</excludeDevtools>
            </configuration>
        </plugin>
        
        <!-- 🍓 Raspi: Index Spring Components (scan rapide) -->
        <plugin>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context-indexer</artifactId>
        </plugin>
    </plugins>
</build>
```
    <!-- Spring Batch -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-batch</artifactId>
    </dependency>
    
    <!-- HSQLDB pour JobRepository (si pas MySQL) -->
    <dependency>
        <groupId>org.hsqldb</groupId>
        <artifactId>hsqldb</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

---

## 📝 Notes de Migration

### Points d'Attention

1. **LawDocumentValidator**: Utilisé partout pour vérifier si traitement nécessaire
2. **Entity Auto-Creation**: Pattern `.orElseGet(() -> LawDocumentEntity.createFromDocumentId())`
3. **Batch Save**: Tous les writers utilisent `lawDocumentService.saveAll()`
4. **Error Handling**: Status FAILED_* avec errorMessage
5. **File Paths**: Utiliser `LawDocumentValidator.get*Path()` pour cohérence

### Compatibilité Ascendante

- Conserver les anciens services avec @Deprecated pendant 1 version
- Rediriger les anciens services vers les nouveaux jobs
- Documentation de migration pour les utilisateurs

### Tests Intégrés Complets

#### Stratégie de Tests

**2 niveaux de tests** :
1. **Tests d'Intégration** : Job complet avec H2 + filesystem (vérif BD + disque)
2. **Tests End-to-End** : Pipeline complet sur Raspberry Pi

**Principe** : Tester directement les jobs complets au lieu de tester chaque composant isolément. Plus rapide, plus proche de la réalité.

---

**pom.xml** (chaque module) :
```xml
<dependencies>
    <!-- H2 Database pour tests -->
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- Spring Boot Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- Spring Batch Test -->
    <dependency>
        <groupId>org.springframework.batch</groupId>
        <artifactId>spring-batch-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**application-test.yml** :
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        
  batch:
    jdbc:
      initialize-schema: always
    job:
      enabled: false  # Pas de lancement automatique
      
law:
  storage:
    base-path: ${java.io.tmpdir}/law-test  # Répertoire temporaire
  batch:
    chunk-size: 5  # Petit chunk pour tests rapides
```

---

#### Configuration H2 pour Tests
```java
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class FetchJobIntegrationTest {
    
    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    
    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;
    
    @Autowired
    private LawDocumentRepository lawDocumentRepository;
    
    @BeforeEach
    void setUp() {
        jobRepositoryTestUtils.removeJobExecutions();
        lawDocumentRepository.deleteAll();
    }
    
    @Test
    @Sql("/data-fetch-test.sql")  // Insérer données test
    void shouldFetchAndSaveDocuments() throws Exception {
        // Given: Documents PENDING en BD
        JobParameters params = new JobParametersBuilder()
            .addString("type", "loi")
            .addLong("time", System.currentTimeMillis())
            .toJobParameters();
        
        // When: Exécution du job
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(params);
        
        // Then: Job complété avec succès
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        
        // Vérification BD: Status mis à jour
        List<LawDocumentEntity> fetched = lawDocumentRepository
            .findByStatusAndType(ProcessingStatus.FETCHED, "loi");
        assertThat(fetched).isNotEmpty();
        
        // Vérification BD: Métadonnées remplies
        LawDocumentEntity doc = fetched.get(0);
        assertThat(doc.getPdfUrl()).isNotBlank();
        assertThat(doc.getDocumentDate()).isNotNull();
        assertThat(doc.getTitle()).isNotBlank();
    }
}
```

---

#### Exemple 2: Tests law-download (DownloadJob)

**Test Intégration avec Filesystem** :
```java
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class DownloadJobIntegrationTest {
    
    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    
    @Autowired
    private LawDocumentRepository repository;
    
    @TempDir
    Path tempStoragePath;  // JUnit 5 temporary directory
    
    @BeforeEach
    void setUp(@Value("${law.storage.base-path}") String basePath) {
        // Override storage path pour tests
        System.setProperty("law.storage.base-path", tempStoragePath.toString());
    }
    
    @Test
    void shouldDownloadPdfAndSaveToFileSystem() throws Exception {
        // Given: Document FETCHED avec URL valide
        LawDocumentEntity doc = LawDocumentEntity.create("loi", 2024, "001");
        doc.setStatus(ProcessingStatus.FETCHED);
        doc.setPdfUrl("https://sgg.gouv.bj/doc/loi/2024/loi-2024-001.pdf");
        repository.save(doc);
        
        JobParameters params = new JobParametersBuilder()
            .addString("type", "loi")
            .addLong("time", System.currentTimeMillis())
            .toJobParameters();
        
        // When: Exécution download job
        JobExecution execution = jobLauncherTestUtils.launchJob(params);
        
        // Then: Job success
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        
        // Vérification BD: Status DOWNLOADED
        LawDocumentEntity updated = repository.findByDocumentId("loi-2024-001").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ProcessingStatus.DOWNLOADED);
        assertThat(updated.getPdfPath()).isNotNull();
        
        // Vérification FileSystem: PDF existe
        Path expectedPdf = tempStoragePath
            .resolve("pdfs")
            .resolve("loi")
            .resolve("loi-2024-001.pdf");
        
        assertThat(Files.exists(expectedPdf)).isTrue();
        assertThat(Files.size(expectedPdf)).isGreaterThan(0);
        
        // Vérification contenu: Header PDF valide
        byte[] header = Files.readAllBytes(expectedPdf).length > 4 
            ? Arrays.copyOf(Files.readAllBytes(expectedPdf), 4) 
            : new byte[0];
        assertThat(header).startsWith("%PDF".getBytes());
    }
    
    @Test
    void shouldHandleCorruptedPdf() throws Exception {
        // Given: Document avec PDF corrompu
        LawDocumentEntity doc = LawDocumentEntity.create("loi", 2024, "002");
        doc.setStatus(ProcessingStatus.FETCHED);
        doc.setPdfUrl("https://invalid-url.com/corrupted.pdf");
        repository.save(doc);
        
        JobParameters params = new JobParametersBuilder()
            .addString("type", "loi")
            .addLong("time", System.currentTimeMillis())
            .toJobParameters();
        
        // When: Exécution
        JobExecution execution = jobLauncherTestUtils.launchJob(params);
        
        // Then: Job completed (skip errors)
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        
        // Vérification BD: Status FAILED_CORRUPTED
        LawDocumentEntity updated = repository.findByDocumentId("loi-2024-002").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ProcessingStatus.FAILED_CORRUPTED);
        assertThat(updated.getErrorMessage()).isNotBlank();
    }
}
```

---

#### Exemple 3: Tests law-pdf-ocr (OcrJob)

**Test Intégration avec Mock Tesseract** :
```java
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class OcrJobIntegrationTest {
    
    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    
    @Autowired
    private LawDocumentRepository repository;
    
    @MockBean
    private TesseractOcrService tesseractService;  // Mock Tesseract (lourd)
    
    @TempDir
    Path tempStoragePath;
    
    @Test
    void shouldPerformOcrAndSaveTextFile() throws Exception {
        // Given: PDF téléchargé
        Path pdfDir = tempStoragePath.resolve("pdfs/loi");
        Files.createDirectories(pdfDir);
        Path pdfFile = pdfDir.resolve("loi-2024-001.pdf");
        Files.writeString(pdfFile, "%PDF-1.4 fake content");
        
        LawDocumentEntity doc = LawDocumentEntity.create("loi", 2024, "001");
        doc.setStatus(ProcessingStatus.DOWNLOADED);
        doc.setPdfPath(pdfFile.toString());
        repository.save(doc);
        
        // Mock Tesseract response
        when(tesseractService.doOcr(any(File.class)))
            .thenReturn("Article 1er : Lorem ipsum dolor sit amet...");
        
        JobParameters params = new JobParametersBuilder()
            .addString("type", "loi")
            .addLong("time", System.currentTimeMillis())
            .toJobParameters();
        
        // When: OCR job
        JobExecution execution = jobLauncherTestUtils.launchJob(params);
        
        // Then: Success
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        
        // Vérification BD: Status OCRED
        LawDocumentEntity updated = repository.findByDocumentId("loi-2024-001").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ProcessingStatus.OCRED);
        assertThat(updated.getOcrPath()).isNotNull();
        
        // Vérification FileSystem: Fichier OCR existe
        Path expectedOcr = tempStoragePath
            .resolve("ocr")
            .resolve("loi")
            .resolve("loi-2024-001.txt");
        
        assertThat(Files.exists(expectedOcr)).isTrue();
        String content = Files.readString(expectedOcr);
        assertThat(content).contains("Article 1er");
        assertThat(content).contains("Lorem ipsum");
    }
}
```

---

#### Configuration Test Base Class

**AbstractIntegrationTest.java** (classe parent réutilisable) :
```java
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "law.storage.base-path=${java.io.tmpdir}/law-test"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class AbstractIntegrationTest {
    
    @Autowired
    protected LawDocumentRepository lawDocumentRepository;
    
    @TempDir
    protected Path tempStoragePath;
    
    @BeforeEach
    void baseSetUp() {
        // Nettoyer BD H2
        lawDocumentRepository.deleteAll();
        
        // Configurer storage temporaire
        System.setProperty("law.storage.base-path", tempStoragePath.toString());
    }
    
    @AfterEach
    void baseTearDown() throws IOException {
        // Nettoyer filesystem
        if (Files.exists(tempStoragePath)) {
            Files.walk(tempStoragePath)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (IOException e) { /* ignore */ }
                });
        }
    }
    
    protected void assertDatabaseState(String documentId, ProcessingStatus expectedStatus) {
        LawDocumentEntity doc = lawDocumentRepository
            .findByDocumentId(documentId)
            .orElseThrow(() -> new AssertionError("Document not found: " + documentId));
        
        assertThat(doc.getStatus())
            .as("Status for " + documentId)
            .isEqualTo(expectedStatus);
    }
    
    protected void assertFileExists(String relativePath) {
        Path file = tempStoragePath.resolve(relativePath);
        assertThat(Files.exists(file))
            .as("File should exist: " + relativePath)
            .isTrue();
    }
}
```

---

#### Checklist Tests par Module

**law-fetch** :
- ✅ Test intégration fetchCurrentJob (H2 + vérif BD métadonnées)
- ✅ Test intégration fetchPreviousJob avec cursor tracking

**law-download** :
- ✅ Test intégration downloadJob (H2 + filesystem)
- ✅ Test vérification PDF existe + header valide
- ✅ Test gestion PDF corrompu + status FAILED_CORRUPTED

**law-pdf-ocr** :
- ✅ Test intégration ocrJob avec mock Tesseract (H2 + filesystem)
- ✅ Test fichier OCR créé + contenu valide
- ✅ Test gestion mémoire (chunk=1)

**law-ocr-json** :
- ✅ Test intégration extractionJob (H2 + JSON filesystem)
- ✅ Test structure JSON valide (schema validation)
- ✅ Test articles extraits correctement

**law-consolidate** :
- ✅ Test intégration consolidateJob (H2)
- ✅ Test articles en BD avec relations correctes
- ✅ Test idempotence (re-run job sans duplication)

---

### Tests End-to-End Pipeline Complet

```java
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class FullPipelineIntegrationTest extends AbstractIntegrationTest {
    
    @Autowired
    private Job fetchJob;
    
    @Autowired
    private Job downloadJob;
    
    @Autowired
    private Job ocrJob;
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Test
    void shouldExecuteFullPipeline() throws Exception {
        // Given: Document initial PENDING
        LawDocumentEntity doc = LawDocumentEntity.create("loi", 2024, "001");
        doc.setStatus(ProcessingStatus.PENDING);
        lawDocumentRepository.save(doc);
        
        JobParameters params = new JobParametersBuilder()
            .addString("type", "loi")
            .addLong("time", System.currentTimeMillis())
            .toJobParameters();
        
        // When: Exécution pipeline complet
        // Step 1: Fetch
        JobExecution fetch = jobLauncher.run(fetchJob, params);
        assertThat(fetch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertDatabaseState("loi-2024-001", ProcessingStatus.FETCHED);
        
        // Step 2: Download
        JobExecution download = jobLauncher.run(downloadJob, params);
        assertThat(download.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertDatabaseState("loi-2024-001", ProcessingStatus.DOWNLOADED);
        assertFileExists("pdfs/loi/loi-2024-001.pdf");
        
        // Step 3: OCR
        JobExecution ocr = jobLauncher.run(ocrJob, params);
        assertThat(ocr.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertDatabaseState("loi-2024-001", ProcessingStatus.OCRED);
        assertFileExists("ocr/loi/loi-2024-001.txt");
        
        // Then: Vérification finale complète
        LawDocumentEntity finalDoc = lawDocumentRepository
            .findByDocumentId("loi-2024-001")
            .orElseThrow();
        
        assertThat(finalDoc.getStatus()).isEqualTo(ProcessingStatus.OCRED);
        assertThat(finalDoc.getPdfUrl()).isNotBlank();
        assertThat(finalDoc.getPdfPath()).isNotBlank();
        assertThat(finalDoc.getOcrPath()).isNotBlank();
        assertThat(finalDoc.getTitle()).isNotBlank();
        assertThat(finalDoc.getErrorMessage()).isNull();
    }
}
```

---

### Tests sur Raspberry Pi 🍓

**Script de test Raspi** :
```bash
#!/bin/bash
# test-raspi.sh - Tests complets sur Raspberry Pi

echo "🍓 Tests Raspberry Pi - io.law"
echo "=============================="

# Test 1: Démarrage application
echo ""
echo "Test 1: Temps démarrage..."
START=$(date +%s)
java $JAVA_OPTS -jar law-app.jar --job=health &
APP_PID=$!
sleep 5
kill $APP_PID 2>/dev/null
END=$(date +%s)
STARTUP_TIME=$((END - START))
echo "✅ Démarrage: ${STARTUP_TIME}s (target: <12s)"

# Test 2: Mémoire après démarrage
echo ""
echo "Test 2: Consommation mémoire..."
java $JAVA_OPTS -jar law-app.jar --job=fetchCurrent --type=loi --maxDocuments=5 &
APP_PID=$!
sleep 10
MEM_USAGE=$(ps -p $APP_PID -o rss= | awk '{print $1/1024}')
echo "✅ Mémoire: ${MEM_USAGE} MB (target: <800 MB)"
kill $APP_PID 2>/dev/null

# Test 3: OCR avec surveillance
echo ""
echo "Test 3: OCR sur 5 PDFs..."
java $JAVA_OPTS -jar law-app.jar --job=ocr --type=loi --maxDocuments=5
echo "✅ OCR complété"

# Test 4: Pipeline complet
echo ""
echo "Test 4: Pipeline end-to-end..."
java $JAVA_OPTS -jar law-app.jar --job=orchestrate --type=loi --maxDocuments=3
echo "✅ Pipeline complété"

echo ""
echo "=============================="
echo "🍓 Tests Raspberry Pi terminés"
```

---

### Tests

- ✅ Tests unitaires pour chaque Reader/Processor/Writer avec H2
- ✅ Tests d'intégration pour chaque Job avec H2 + filesystem temporaire
- **Tests sur Raspberry Pi** 🍓 : Validation mémoire, swap, performance
- Tests de charge avec surveillance heap memory

### Configuration Raspberry Pi Recommandée

**Hardwared'intégration pour chaque Job avec H2 + filesystem temporaire
- ✅ Vérification BD (status, métadonnées) ET disque (fichiers existent, contenu valide)
- ✅ Tests end-to-end pour pipeline complet (fetch → download → ocr → extract → consolidate)
- ✅ **Tests sur Raspberry Pi** 🍓 : Validation mémoire, swap, performance
- ✅ Tests de charge avec surveillance heap memory
- ✅ Mock Tesseract pour tests OCR (éviter dépendance lourde)

**Pas de tests unitaires** : Tests d'intégration directement sur jobs complets (plus rapide, plus réaliste)
- Carte SD 64 GB Class 10 (ou SSD USB pour meilleures perfs)
- Swap configuré : 2-4 GB

**application.properties optimisé Raspi** :
```properties
# JVM Memory
spring.jpa.properties.hibernate.jdbc.batch_size=20
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true

# Batch Chunk Sizes (réduits pour Raspi)
batch.chunk.fetch=50
batch.chunk.download=20
batch.chunk.ocr=1          # OCR CRITIQUE: 1 seul PDF à la fois
batch.chunk.extraction=30
batch.chunk.consolidate=50

# OCR Tesseract Configuration (Raspi)
ocr.tesseract.threads=1
ocr.tesseract.dpi=150      # Réduit de 300 à 150
ocr.tesseract.timeout=300  # 5 min max par PDF
ocr.memory.check=true      # Vérifier mémoire avant OCR
ocr.memory.threshold=200   # Minimum 200 MB libre requis
ocr.gc.force=true          # Forcer GC après chaque PDF

# Connection Pool (réduit)
spring.datasource.hikari.maximum-pool-size=5
spring.datasource.hikari.minimum-idle=2

# Logging (éviter surcharge)
logging.level.org.springframework.batch=INFO
logging.level.bj.gouv.sgg=INFO
```

**Lancement optimisé Raspi** :
```bash
#!/bin/bash
# law-app-raspi.sh - Script optimisé pour Raspberry Pi

echo "🍓 Préparation démarrage Raspberry Pi..."

# Configuration JVM pour Raspberry Pi (démarrage + runtime)
export JAVA_OPTS="-Xms256m -Xmx1024m \
  -XX:+UseSerialGC \
  -XX:MaxMetaspaceSize=256m \
  -XX:TieredStopAtLevel=1 \
  -XX:+UseStringDeduplication \
  -Djava.awt.headless=true \
  -Dfile.encoding=UTF-8 \
  -Dspring.main.lazy-initialization=true \
  -Dspring.jmx.enabled=false"

# 🍓 Optimisations startup spécifiques
export JAVA_OPTS="$JAVA_OPTS \
  -noverify \
  -XX:+TieredCompilation \
  -XX:TieredStopAtLevel=1"

# Configuration Tesseract (limiter threads)
export OMP_THREAD_LIMIT=1
export TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata

# Vérifications pré-démarrage
echo "📊 État système avant démarrage:"
echo "   Mémoire: $(free -h | grep Mem | awk '{print $3 "/" $2}')"
echo "   Swap: $(free -h | grep Swap | awk '{print $3 "/" $2}')"
echo "   Charge: $(uptime | awk -F'load average:' '{print $2}')"

# Surveillance mémoire en arrière-plan
watch -n 5 'free -h && echo "---" && ps aux --sort=-%mem | head -5' &
WATCH_PID=$!

# Lancement application avec timer
echo ""
echo "🚀 Démarrage application..."
START_TIME=$(date +%s)

java $JAVA_OPTS -jar law-app.jar "$@"
EXIT_CODE=$?

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

# Cleanup
kill $WATCH_PID 2>/dev/null

# Rapport final
echo ""
echo "✅ Application terminée"
echo "📊 Statistiques finales:"
echo "   Durée exécution: ${DURATION}s"
echo "   Mémoire finale: $(free -h | grep Mem | awk '{print $3 "/" $2}')"
echo "   Exit code: $EXIT_CODE"

exit $EXIT_CODE
```

**Benchmark démarrage attendu sur Raspi** :
```
Sans optimisations : 30-45 secondes
Avec optimisations  : 8-12 secondes  ✅

Gain : ~70% de réduction temps démarrage
```

---

**Monitoring OCR spécifique Raspi** :
```bash
#!/bin/bash
# monitor-ocr-raspi.sh - Surveiller l'OCR en temps réel

echo "🍓 Monitoring OCR sur Raspberry Pi"
echo "=================================="

# Lancer job OCR en arrière-plan
java $JAVA_OPTS -jar law-app.jar --job=ocr --type=loi &
APP_PID=$!

# Surveiller mémoire pendant l'exécution
while kill -0 $APP_PID 2>/dev/null; do
    clear
    echo "🍓 OCR en cours (PID: $APP_PID)"
    echo "=================================="
    
    # Mémoire système
    free -h | grep -E "Mem|Swap"
    echo ""
    
    # Processus Java
    ps aux | grep java | grep -v grep | awk '{printf "Java: CPU=%s%% MEM=%s%% RSS=%s\n", $3, $4, $6}'
    echo ""
    
    # Heap JVM (si jstat disponible)
    jstat -gc $APP_PID 2>/dev/null | tail -1 | awk '{printf "Heap: Used=%.0f MB\n", ($3+$4+$6+$8)/1024}'
    echo ""
    
    # Logs récents
    tail -3 logs/law-app.log 2>/dev/null
    
    sleep 5
done

echo ""
echo "✅ OCR terminé"
wait $APP_PID
echo "Exit code: $?"
```

---

## � Points Importants Non Couverts (À Ajouter)

### 1. Migration law-app (Module Orchestrateur) 🚨

**Actuellement** :
```java
// law-app/src/main/java/bj/gouv/sgg/app/LawApp.java
public class LawApp {
    public static void main(String[] args) {
        String job = args[0];  // --job=fetch
        String type = args[1]; // --type=loi
        
        if ("fetch".equals(job)) {
            FetchJob.getInstance().run(type);
        }
    }
}
```

**Après migration Spring Batch** :
```java
@SpringBootApplication
@EnableBatchProcessing
public class LawApplication implements CommandLineRunner {
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Autowired
    private Map<String, Job> jobs;  // Tous les jobs injectés
    
    @Override
    public void run(String... args) throws Exception {
        // Parser arguments CLI
        String jobName = getArg(args, "--job");      // fetchCurrent, download, ocr...
        String type = getArg(args, "--type");        // loi, decret
        Integer maxDocs = getIntArg(args, "--maxDocuments");
        
        // Construire JobParameters
        JobParameters params = new JobParametersBuilder()
            .addString("type", type)
            .addLong("maxDocuments", maxDocs != null ? maxDocs : Long.MAX_VALUE)
            .addLong("timestamp", System.currentTimeMillis())  // Rendre unique
            .toJobParameters();
        
        // Lancer le job demandé
        Job job = jobs.get(jobName + "Job");  // Ex: fetchCurrentJob
        if (job == null) {
            log.error("❌ Job inconnu: {}", jobName);
            System.exit(1);
        }
        
        JobExecution execution = jobLauncher.run(job, params);
        
        if (execution.getStatus() == BatchStatus.COMPLETED) {
            log.info("✅ Job {} terminé avec succès", jobName);
            System.exit(0);
        } else {
            log.error("❌ Job {} échoué: {}", jobName, execution.getStatus());
            System.exit(1);
        }
    }
    
    private String getArg(String[] args, String key) {
        return Arrays.stream(args)
            .filter(arg -> arg.startsWith(key + "="))
            .map(arg -> arg.substring(key.length() + 1))
            .findFirst()
            .orElse(null);
    }
}
```

**Utilisation** :
```bash
# Avant migration
java -jar law-app.jar fetch loi

# Après migration Spring Batch
java -jar law-app.jar --job=fetchCurrent --type=loi --maxDocuments=100
java -jar law-app.jar --job=download --type=decret
java -jar law-app.jar --job=ocr --type=loi
java -jar law-app.jar --job=orchestrate --type=loi  # Pipeline complet
```

---

### 2. Gestion des Erreurs et Retry Policy 🔄

**Skip Policy** (sauter erreurs sans stopper le job) :
```java
@Bean
public Step downloadStep(...) {
    return new StepBuilder("downloadStep", jobRepository)
        .<LawDocumentEntity, DownloadResult>chunk(20, transactionManager)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .faultTolerant()
        .skip(IOException.class)              // Skip erreurs réseau
        .skip(CorruptedPdfException.class)    // Skip PDF corrompus
        .skipLimit(Integer.MAX_VALUE)         // Pas de limite
        .noSkip(FatalException.class)         // Arrêter sur erreurs fatales
        .build();
}
```

**Retry Policy** (réessayer sur erreurs temporaires) :
```java
@Bean
public Step fetchStep(...) {
    return new StepBuilder("fetchStep", jobRepository)
        .<LawDocumentEntity, FetchResult>chunk(50, transactionManager)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .faultTolerant()
        .retry(ConnectException.class)         // Retry erreurs connexion
        .retry(SocketTimeoutException.class)   // Retry timeout
        .retryLimit(3)                         // Max 3 tentatives
        .backOffPolicy(new ExponentialBackOffPolicy() {{
            setInitialInterval(1000);   // 1 seconde
            setMultiplier(2);            // x2 à chaque tentative
            setMaxInterval(10000);       // Max 10 secondes
        }})
        .build();
}
```

**Listeners pour logging** :
```java
@Component
public class JobCompletionListener implements JobExecutionListener {
    
    @Override
    public void afterJob(JobExecution execution) {
        long duration = execution.getEndTime().getTime() - execution.getStartTime().getTime();
        
        if (execution.getStatus() == BatchStatus.COMPLETED) {
            log.info("✅ Job {} complété en {}ms", 
                     execution.getJobInstance().getJobName(), 
                     duration);
        } else {
            List<Throwable> failures = execution.getAllFailureExceptions();
            log.error("❌ Job {} échoué après {}ms. Erreurs: {}", 
                      execution.getJobInstance().getJobName(),
                      duration,
                      failures);
        }
    }
}
```

---

### 3. Monitoring et Observabilité 📊

**JobExplorer** pour consulter historique :
```java
@RestController
@RequestMapping("/api/jobs")
public class JobMonitoringController {
    
    @Autowired
    private JobExplorer jobExplorer;
    
    @GetMapping("/status/{jobName}")
    public JobStatus getJobStatus(@PathVariable String jobName) {
        List<JobInstance> instances = jobExplorer.findJobInstancesByJobName(jobName, 0, 10);
        
        return instances.stream()
            .map(instance -> {
                JobExecution lastExecution = jobExplorer.getLastJobExecution(instance);
                return new JobStatus(
                    instance.getJobName(),
                    lastExecution.getStatus(),
                    lastExecution.getStartTime(),
                    lastExecution.getEndTime()
                );
            })
            .findFirst()
            .orElse(null);
    }
}
```

**Métriques Spring Batch** :
```yaml
management:
  metrics:
    export:
      simple:
        enabled: true
  endpoint:
    metrics:
      enabled: true
```

```java
// Accès aux métriques
curl http://localhost:8080/actuator/metrics/spring.batch.job
curl http://localhost:8080/actuator/metrics/spring.batch.step
```

---

### 4. Credentials et Secrets 🔐

**Pas de hardcoding** :
```properties
# ❌ PAS ça
spring.datasource.url=jdbc:mysql://localhost:3306/law_db
spring.datasource.username=root
spring.datasource.password=root

# ✅ Variables d'environnement
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/law_db}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:root}

# API Keys
groq.api.key=${GROQ_API_KEY:}
ollama.base.url=${OLLAMA_URL:http://localhost:11434}
```

**Script de lancement sécurisé** :
```bash
#!/bin/bash
# Charger secrets depuis fichier .env
export $(grep -v '^#' .env | xargs)

# Lancer application
java -jar law-app.jar "$@"
```

**.env.example** :
```bash
DB_URL=jdbc:mysql://localhost:3306/law_db
DB_USERNAME=root
DB_PASSWORD=change_me
GROQ_API_KEY=your_api_key_here
OLLAMA_URL=http://localhost:11434
```

---

### 5. Logging Structuré 📝

**Configuration logback-spring.xml** :
```xml
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/law-app.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/law-app.%d{yyyy-MM-dd}.log.gz</fileNamePattern>
            <maxHistory>30</maxHistory>
            <totalSizeCap>1GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- 🍓 Raspi: Logs moins verbeux -->
    <logger name="org.springframework" level="WARN"/>
    <logger name="org.hibernate" level="WARN"/>
    <logger name="bj.gouv.sgg" level="INFO"/>
    
    <root level="INFO">
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

---

### 6. Rollback Strategy 🔄

**Branches Git** :
```bash
# Créer branche migration
git checkout -b feature/spring-batch-migration

# Si échec, revenir à l'ancien code
git checkout migration/rearchitecture-tojson
mvn clean package
java -jar law-app/target/law-app-1.0.0-SNAPSHOT.jar
```

**Compatibilité ascendante** :
```java
// Garder anciens services @Deprecated
@Service
@Deprecated(since = "2.0.0", forRemoval = true)
public class FetchCurrentServiceImpl {
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Autowired
    private Job fetchCurrentJob;
    
    public void run(String type) {
        log.warn("⚠️ Ancien service appelé. Utiliser Spring Batch Job à la place.");
        
        // Rediriger vers nouveau job
        JobParameters params = new JobParametersBuilder()
            .addString("type", type)
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
        
        try {
            jobLauncher.run(fetchCurrentJob, params);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

---

### 7. Exclusions Maven pour JAR Léger 📦

**Parent pom.xml** :
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <excludes>
                    <!-- 🍓 Raspi: Réduire taille JAR -->
                    <exclude>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-devtools</artifactId>
                    </exclude>
                </excludes>
            </configuration>
        </plugin>
    </plugins>
</build>
```

**Dépendances avec scope** :
```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>  <!-- Pas en production -->
</dependency>
```

---

### 8. CI/CD Pipeline 🚀

**.github/workflows/test.yml** :
```yaml
name: Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          
      - name: Cache Maven packages
        uses: actions/cache@v3
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
          
      - name: Run tests
        run: mvn clean test
        
      - name: Build JAR
        run: mvn package -DskipTests
        
      - name: Upload JAR
        uses: actions/upload-artifact@v3
        with:
          name: law-app
          path: law-app/target/*.jar
```

---

## 🚀 Prochaines Étapes (Mis à Jour)

1. **Valider le plan** avec l'équipe
2. **Créer une branche** `feature/spring-batch-migration`
3. **Commencer par law-pdf-ocr** (module critique)
4. **Pull Request** avec tests complets
5. **Review et merge**
6. **Itérer** sur les autres modules
7. **Finaliser** avec orchestration complète

---

**Date de création**: 18 décembre 2025
**Statut**: 📋 PLAN PRÊT - En attente de validation
