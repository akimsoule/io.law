# État de la Migration Spring Batch - io.law

**Date**: 18 décembre 2024  
**Branche**: `migration/spring-batch`  
**Version cible**: Spring Boot 3.5.9

---

## ✅ Phase 1: law-common (TERMINÉ)

### Accomplissements

#### 1. Configuration Spring Boot ✅
- Parent POM Spring Boot 3.5.9 ajouté
- `@EnableAutoConfiguration` dans CommonConfiguration
- spring-boot.version: 3.5.9
- Dépendances Spring Boot BOM configurées

#### 2. Migration vers Spring Data JPA ✅
- Repository migré de JPA natif vers Spring Data JPA
- Interface `LawDocumentRepository extends JpaRepository`
- Méthodes query dérivées et @Query personnalisées
- Singleton patterns supprimés

#### 3. Services Spring ✅
- `LawDocumentService` → `@Service` avec injection
- `LawDocumentValidator` → injection de dépendances
- `FileStorageService` → `@Component` avec @ConfigurationProperties
- Suppression complète des singletons

#### 4. Configuration Properties ✅
- `AppConfig` → `@ConfigurationProperties("law")`
- Type-safe configuration avec validation
- Prefix hierarchique (law.api, law.paths, etc.)

#### 5. Tests Professionnels ✅
- **12 tests** JUnit 5 avec Spring Boot Test
- Format BDD: `givenWhenThen` (camelCase, sans underscores)
- `@SpringBootTest` avec H2 in-memory
- `LawDocumentServiceIntegrationTest` (8 tests)
- `LawDocumentServiceSimpleTest` (4 tests)
- 100% de succès, aucune sortie console

#### 6. Structure Modules Maven ✅
```
io.law (parent)
├── law-common ✅ (Spring Boot migré)
├── law-fetch (à migrer)
├── law-download (à migrer)
├── law-tojson/ (à migrer)
│   ├── law-pdf-ocr
│   ├── law-ocr-json
│   ├── law-ai
│   ├── law-json-config
│   └── law-qa
└── law-app (à migrer)
```

#### 7. Qualité du Code ✅
- Aucun `System.out.println` dans les tests
- Noms de méthodes conformes à `^[a-z][a-zA-Z0-9]*$`
- Convention BDD respectée
- Documentation claire
- Pas de code mort

### Dépendances Spring Boot 3.5.9

```xml
<!-- Versions gérées automatiquement -->
<spring-framework.version>6.2.15</spring-framework.version>
<hibernate.version>6.6.39.Final</hibernate.version>
<spring-data.version>3.5.7</spring-data.version>
<mockito.version>5.17.0</mockito.version>
<junit-jupiter.version>5.12.2</junit-jupiter.version>
```

### Commits Réalisés

1. ✅ `feat(law-common): Migrer vers Spring Data JPA`
2. ✅ `refactor(law-common): Supprimer singleton LawDocumentValidator`
3. ✅ `build: Upgrade Spring Boot 3.2.0 → 3.5.9`
4. ✅ `build(parent): Nettoyer POM parent`
5. ✅ `test(law-common): Tests Spring Boot professionnels`
6. ✅ `refactor(law-common): Renommer TestDocumentServiceMySQL → LawDocumentServiceSimpleTest`
7. ✅ `style(law-common): Convention givenWhenThen pour tests`

---

## 🔄 Phase 2: law-fetch (À FAIRE)

### Objectifs

1. Migrer vers Spring Batch Jobs
   - `fetchCurrentJob`: Scan année courante
   - `fetchPreviousJob`: Scan historique avec cursor
   
2. Configuration Spring Batch
   - JobRepository H2/MySQL
   - StepBuilder avec chunk processing
   - Skip/Retry policies

3. Composants Batch
   - `DocumentItemReader`: Génère URLs à vérifier
   - `DocumentAvailabilityProcessor`: HEAD request
   - `DocumentMetadataWriter`: Sauvegarde dans BD

4. Tests
   - Tests unitaires des composants
   - Tests d'intégration Spring Batch
   - JobLauncherTestUtils

### Pattern Cible

```java
@Configuration
@EnableBatchProcessing
public class FetchJobConfiguration {
    
    @Bean
    public Job fetchCurrentJob(JobRepository jobRepository,
                              Step fetchCurrentStep) {
        return new JobBuilder("fetchCurrentJob", jobRepository)
            .start(fetchCurrentStep)
            .build();
    }
    
    @Bean
    public Step fetchCurrentStep(JobRepository jobRepository,
                                PlatformTransactionManager transactionManager,
                                ItemReader<DocumentUrl> reader,
                                ItemProcessor<DocumentUrl, LawDocumentEntity> processor,
                                ItemWriter<LawDocumentEntity> writer) {
        return new StepBuilder("fetchCurrentStep", jobRepository)
            .<DocumentUrl, LawDocumentEntity>chunk(50, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
    }
}
```

---

## 📋 Phase 3: law-download (À FAIRE)

### Objectifs

1. Job Spring Batch pour téléchargement PDFs
2. Composants:
   - `PendingDocumentReader`: Documents status=FETCHED
   - `PdfDownloadProcessor`: Télécharge PDF
   - `PdfFileWriter`: Sauvegarde sur disque + update BD

3. Gestion erreurs
   - Skip si 404/403
   - Retry si timeout/erreur réseau
   - Logging des échecs

---

## 📋 Phase 4: law-tojson (À FAIRE)

### Modules à migrer

#### 4.1 law-pdf-ocr
- Job OCR avec Tesseract
- **ATTENTION**: Chunk size = 1 pour Raspberry Pi
- Libération mémoire forcée après chaque PDF

#### 4.2 law-ocr-json
- Parsing OCR → JSON structuré
- Extraction articles/sections

#### 4.3 law-ai
- Parsing IA (Ollama/Groq)
- Fallback si OCR échoue

#### 4.4 law-json-config
- Configuration partagée modules tojson

#### 4.5 law-qa
- Validation qualité
- Génération rapports

---

## 🎯 Phase 5: law-app (À FAIRE)

### Objectifs

1. Orchestrateur Spring Boot Application
2. CLI avec arguments
3. Orchestration jobs séquentiels
4. Monitoring et métriques

---

## 📊 Statistiques

### Code Migré
- **1 module** sur 6 ✅ (16.7%)
- **~3,000 lignes** de code refactoré
- **12 tests** professionnels écrits
- **0 défauts** détectés

### Reste à Migrer
- **5 modules** (law-fetch, law-download, law-tojson/*, law-app)
- **~15,000 lignes** estimées
- **~50-80 tests** à écrire

### Temps Estimé
- ✅ law-common: **2 jours** (FAIT)
- ⏳ law-fetch: **1-2 jours**
- ⏳ law-download: **1 jour**
- ⏳ law-tojson: **2-3 jours**
- ⏳ law-app: **1 jour**
- ⏳ Tests finaux: **1 jour**

**Total**: ~8-10 jours

---

## 🚀 Prochaines Étapes

1. **law-fetch**: Migrer vers Spring Batch Jobs
2. **law-download**: Job téléchargement PDFs
3. **law-pdf-ocr**: Job OCR (attention RAM Raspberry Pi)
4. **law-ocr-json + law-ai**: Jobs parsing
5. **law-app**: Orchestration finale
6. **Tests end-to-end**: Workflow complet

---

## 📖 Documentation

- [MIGRATION-STEP-BY-STEP.md](MIGRATION-STEP-BY-STEP.md) - Guide détaillé
- [PLAN-MIGRATION-SPRING-BATCH.md](PLAN-MIGRATION-SPRING-BATCH.md) - Plan complet
- [README.md](README.md) - Documentation générale
- [.github/copilot-instructions.md](.github/copilot-instructions.md) - Instructions Copilot

---

## ⚠️ Notes Importantes

### Raspberry Pi Constraints
- **Heap max**: 1 GB (`-Xmx1024m`)
- **Chunk size OCR**: **1** (impératif!)
- **Tesseract threads**: 1 (`OMP_THREAD_LIMIT=1`)
- **Serial GC**: `-XX:+UseSerialGC`

### Tests
- H2 in-memory pour tests
- MySQL pour production
- Convention BDD: `givenWhenThen` camelCase

### Versions
- Java: **17+**
- Spring Boot: **3.5.9**
- Spring Framework: **6.2.15**
- Hibernate: **6.6.39**
