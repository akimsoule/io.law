# Refactorisation Architecture law-tojson → Architecture law-consolidate

## 🎯 Objectif
Aligner les modules law-tojson sur l'architecture standardisée de law-consolidate.

## 📐 Architecture law-consolidate (Modèle à suivre)

```
law-consolidate/
├── src/main/java/bj/gouv/sgg/
│   ├── exception/               # Exceptions spécifiques module
│   │   └── ConsolidationException.java
│   ├── job/                     # Jobs délèguent au service
│   │   └── ConsolidateJob.java
│   ├── model/                   # Modèles métier
│   │   ├── ConsolidationResult.java
│   │   └── DocumentRecord.java
│   ├── repository/              # Repositories JPA/JsonStorage
│   │   └── ConsolidationResultRepository.java
│   └── service/                 # Interfaces + Implémentations
│       ├── ConsolidationService.java (interface)
│       └── impl/
│           └── ConsolidationServiceImpl.java
```

### Principes
1. **Interface `XxxService`** : Définit le contrat métier
2. **Implémentation `XxxServiceImpl`** : Logique métier dans `service/impl/`
3. **Job `XxxJob`** : Point d'entrée minimal, délègue au service
4. **Exceptions** : Héritent de `LawProcessingException`, dans `exception/`
5. **Non-bloquant** : Toute exception catchée, log + continue

---

## 🔄 Refactorisation par Module

### 1. law-pdf-ocr

#### Architecture Actuelle
```
law-pdf-ocr/
└── src/main/java/bj/gouv/sgg/
    ├── exception/
    │   ├── OcrProcessingException.java ✅
    │   └── TesseractInitializationException.java ✅
    ├── impl/                    ❌ Pas service/impl/
    │   ├── PdfMagicByteDetector.java
    │   ├── TesseractOcrExtractor.java
    │   └── (logique éparpillée)
    └── service/
        └── OcrService.java (interface) ✅
```

#### Architecture Cible
```
law-pdf-ocr/
└── src/main/java/bj/gouv/sgg/
    ├── exception/               ✅ OK
    ├── job/                     ➕ À CRÉER
    │   └── OcrJob.java
    ├── model/                   ➕ À CRÉER si besoin
    │   └── OcrResult.java (optionnel)
    ├── repository/              ➕ À CRÉER si besoin
    │   └── OcrResultRepository.java (optionnel)
    └── service/
        ├── OcrService.java (interface) ✅ OK
        └── impl/                ➕ DÉPLACER DEPUIS impl/
            └── OcrServiceImpl.java (regroupe logique)
```

#### Actions
1. ✅ **GARDER** : `exception/` (déjà conforme)
2. ✅ **GARDER** : `service/OcrService.java` (interface)
3. ➕ **CRÉER** : `service/impl/OcrServiceImpl.java`
   - Déplacer logique depuis `impl/TesseractOcrExtractor.java`
   - Intégrer `PdfMagicByteDetector` comme méthode privée
4. ➕ **CRÉER** : `job/OcrJob.java`
   - Délègue à `OcrServiceImpl`
   - Méthodes : `runDocument(String documentId)`, `run(String type)`
5. 🗑️ **SUPPRIMER** : `impl/` (déplacer contenu vers `service/impl/`)

---

### 2. law-ocr-json

#### Architecture Actuelle
```
law-ocr-json/
└── src/main/java/bj/gouv/sgg/
    ├── config/                  ✅ OK
    │   └── ArticleExtractorConfig.java
    ├── exception/               ✅ OK (modifié)
    │   ├── ConfigurationException.java ✅
    │   └── OcrExtractionException.java ✅
    ├── impl/                    ❌ Pas service/impl/
    │   └── ArticleRegexExtractor.java
    ├── model/                   ✅ OK
    │   ├── Article.java
    │   ├── DocumentMetadata.java
    │   └── Signatory.java
    ├── ocr/                     ❌ Logique éparpillée
    │   └── OcrAnalyzer.java
    └── service/
        └── OcrExtractionService.java (interface) ✅
```

#### Architecture Cible
```
law-ocr-json/
└── src/main/java/bj/gouv/sgg/
    ├── config/                  ✅ OK
    ├── exception/               ✅ OK
    ├── job/                     ➕ À CRÉER
    │   └── ArticleExtractionJob.java
    ├── model/                   ✅ OK
    ├── repository/              ➕ À CRÉER si besoin
    │   └── OcrExtractionResultRepository.java (optionnel)
    └── service/
        ├── OcrExtractionService.java (interface) ✅ OK
        └── impl/                ➕ DÉPLACER DEPUIS impl/ + ocr/
            └── OcrExtractionServiceImpl.java (regroupe logique)
```

#### Actions
1. ✅ **GARDER** : `config/`, `exception/`, `model/`
2. ✅ **GARDER** : `service/OcrExtractionService.java` (interface)
3. ➕ **CRÉER** : `service/impl/OcrExtractionServiceImpl.java`
   - Déplacer logique depuis `impl/ArticleRegexExtractor.java`
   - Intégrer `ocr/OcrAnalyzer.java` comme méthode privée
4. ➕ **CRÉER** : `job/ArticleExtractionJob.java`
   - Délègue à `OcrExtractionServiceImpl`
5. 🗑️ **SUPPRIMER** : `impl/`, `ocr/` (déplacer contenu vers `service/impl/`)

---

### 3. law-ai

#### Architecture Actuelle (Complexe)
```
law-ai/
└── src/main/java/bj/gouv/sgg/
    ├── ai/                      ❌ Logique éparpillée
    │   ├── chunking/
    │   │   ├── ChunkingService.java (interface)
    │   │   ├── JsonChunker.java
    │   │   └── TextChunker.java
    │   ├── model/
    │   │   ├── AIRequest.java
    │   │   ├── AIResponse.java
    │   │   ├── TransformationContext.java
    │   │   └── TransformationResult.java
    │   ├── provider/
    │   │   ├── IAProvider.java (interface)
    │   │   ├── IAProviderFactory.java
    │   │   └── impl/
    │   │       ├── GroqProvider.java
    │   │       └── OllamaProvider.java
    │   ├── service/
    │   │   ├── AIOrchestrator.java
    │   │   ├── ChunkingService.java
    │   │   └── PromptLoader.java
    │   └── transformation/
    │       ├── IATransformation.java (interface)
    │       └── impl/
    │           ├── OcrCorrectionTransformation.java
    │           └── OcrToJsonTransformation.java
    ├── exception/               ✅ OK (modifié)
    │   ├── IAException.java ✅
    │   ├── IAExtractionException.java ✅
    │   └── PromptLoadException.java ✅
    ├── impl/                    ❌ Mal placé
    │   └── OllamaClient.java
    ├── modele/                  ❌ Typo + mal placé
    │   └── JsonResult.java
    └── service/                 ❌ Interface manquante
        └── IAService.java (interface) ✅ OK
```

#### Architecture Cible (Simplifiée)
```
law-ai/
└── src/main/java/bj/gouv/sgg/
    ├── exception/               ✅ OK
    ├── job/                     ➕ À CRÉER
    │   └── IAJob.java
    ├── model/                   ➕ RENOMMER + CONSOLIDER
    │   ├── AIRequest.java (depuis ai/model/)
    │   ├── AIResponse.java
    │   ├── JsonResult.java (depuis modele/)
    │   ├── TransformationContext.java
    │   └── TransformationResult.java
    ├── provider/                ➕ DÉPLACER DEPUIS ai/provider/
    │   ├── IAProvider.java (interface)
    │   ├── IAProviderFactory.java
    │   └── impl/
    │       ├── GroqProvider.java
    │       └── OllamaProvider.java
    ├── repository/              ➕ À CRÉER si besoin
    │   └── IAResultRepository.java (optionnel)
    └── service/
        ├── IAService.java (interface) ✅ OK
        └── impl/                ➕ CRÉER + CONSOLIDER
            └── IAServiceImpl.java (regroupe toute la logique)
```

#### Actions
1. ✅ **GARDER** : `exception/`
2. ➕ **RENOMMER** : `modele/` → `model/`
3. ➕ **DÉPLACER** : `ai/model/*` → `model/`
4. ➕ **DÉPLACER** : `ai/provider/` → `provider/` (racine bj.gouv.sgg)
5. ➕ **CRÉER** : `service/impl/IAServiceImpl.java`
   - Intégrer logique de `ai/service/AIOrchestrator.java`
   - Intégrer logique de `ai/transformation/`
   - Intégrer logique de `ai/chunking/`
   - Intégrer `impl/OllamaClient.java`
   - Méthodes : `correctOcr()`, `ocrToJson()`, `correctJson()`, `pdfToJson()`
6. ➕ **CRÉER** : `job/IAJob.java`
   - Délègue à `IAServiceImpl`
7. 🗑️ **SUPPRIMER** : `ai/`, `impl/`, `modele/` (après déplacement)

---

### 4. law-json-config (Orchestrateur)

#### Architecture Actuelle
```
law-json-config/
└── src/main/java/bj/gouv/sgg/
    ├── config/                  ✅ OK
    │   ├── ArticleExtractorConfig.java
    │   └── IAServiceConfiguration.java
    ├── impl/                    ❌ Mal placé
    │   └── ArticleRegexExtractor.java
    ├── model/                   ✅ OK
    │   ├── LawDocument.java
    │   └── ProcessingStatus.java
    ├── modele/                  ❌ Typo
    │   └── JsonResult.java
    ├── processor/               ✅ OK (si batch)
    │   └── PdfToJsonProcessor.java
    └── service/                 ❌ Pas d'interface
        ├── LawTransformationService.java
        └── OcrTransformer.java
```

#### Architecture Cible
```
law-json-config/
└── src/main/java/bj/gouv/sgg/
    ├── config/                  ✅ OK
    ├── job/                     ➕ À CRÉER
    │   └── PdfToJsonJob.java
    ├── model/                   ➕ CONSOLIDER
    │   ├── JsonResult.java (depuis modele/)
    │   ├── LawDocument.java
    │   └── ProcessingStatus.java
    ├── processor/               ✅ OK (batch ItemProcessor)
    │   └── PdfToJsonProcessor.java
    └── service/
        ├── TransformationService.java (interface) ➕ À CRÉER
        └── impl/                ➕ CRÉER
            └── TransformationServiceImpl.java (regroupe logique)
```

#### Actions
1. ✅ **GARDER** : `config/`, `processor/`
2. ➕ **RENOMMER** : `modele/` → `model/`
3. ➕ **CRÉER** : `service/TransformationService.java` (interface)
4. ➕ **CRÉER** : `service/impl/TransformationServiceImpl.java`
   - Intégrer logique de `LawTransformationService.java`
   - Intégrer logique de `OcrTransformer.java`
5. ➕ **CRÉER** : `job/PdfToJsonJob.java` (si nécessaire)
6. 🗑️ **SUPPRIMER** : `impl/`, `modele/` (après déplacement)

---

### 5. law-qa (Quality Assurance)

#### Architecture Actuelle
```
law-qa/
└── src/main/java/bj/gouv/sgg/qa/
    └── service/
        ├── JsonQualityService.java (interface) ✅
        ├── OcrQualityService.java (interface) ✅
        └── UnrecognizedWordsService.java (interface) ✅
```

#### Architecture Cible (À compléter)
```
law-qa/
└── src/main/java/bj/gouv/sgg/qa/
    ├── exception/               ➕ À CRÉER
    │   └── QAException.java
    ├── job/                     ➕ À CRÉER
    │   ├── JsonQualityJob.java
    │   └── OcrQualityJob.java
    ├── model/                   ➕ À CRÉER
    │   ├── QualityReport.java
    │   └── WordStatistics.java
    └── service/
        ├── JsonQualityService.java ✅ OK
        ├── OcrQualityService.java ✅ OK
        ├── UnrecognizedWordsService.java ✅ OK
        └── impl/                ➕ À CRÉER
            ├── JsonQualityServiceImpl.java
            ├── OcrQualityServiceImpl.java
            └── UnrecognizedWordsServiceImpl.java
```

---

## 📋 Checklist Complète

### Phase 1 : law-pdf-ocr
- [ ] Créer `service/impl/OcrServiceImpl.java`
- [ ] Déplacer logique depuis `impl/TesseractOcrExtractor.java`
- [ ] Intégrer `PdfMagicByteDetector` comme méthode privée
- [ ] Créer `job/OcrJob.java`
- [ ] Supprimer `impl/` (après validation)
- [ ] Compiler et tester

### Phase 2 : law-ocr-json
- [ ] Créer `service/impl/OcrExtractionServiceImpl.java`
- [ ] Déplacer logique depuis `impl/ArticleRegexExtractor.java`
- [ ] Intégrer `ocr/OcrAnalyzer.java`
- [ ] Créer `job/ArticleExtractionJob.java`
- [ ] Supprimer `impl/`, `ocr/` (après validation)
- [ ] Compiler et tester

### Phase 3 : law-ai
- [ ] Renommer `modele/` → `model/`
- [ ] Déplacer `ai/model/*` → `model/`
- [ ] Déplacer `ai/provider/` → `provider/` (racine)
- [ ] Créer `service/impl/IAServiceImpl.java`
- [ ] Intégrer logique `AIOrchestrator`, `transformations`, `chunking`
- [ ] Créer `job/IAJob.java`
- [ ] Supprimer `ai/`, `impl/`, `modele/` (après validation)
- [ ] Compiler et tester

### Phase 4 : law-json-config
- [ ] Renommer `modele/` → `model/`
- [ ] Créer `service/TransformationService.java` (interface)
- [ ] Créer `service/impl/TransformationServiceImpl.java`
- [ ] Intégrer logique `LawTransformationService`, `OcrTransformer`
- [ ] Créer `job/PdfToJsonJob.java` (optionnel)
- [ ] Supprimer `impl/`, `modele/` (après validation)
- [ ] Compiler et tester

### Phase 5 : law-qa
- [ ] Créer `exception/QAException.java`
- [ ] Créer `model/QualityReport.java`, `WordStatistics.java`
- [ ] Créer `service/impl/` avec 3 implémentations
- [ ] Créer `job/JsonQualityJob.java`, `OcrQualityJob.java`
- [ ] Compiler et tester

### Phase 6 : Validation Globale
- [ ] Compilation complète : `mvn clean install -DskipTests`
- [ ] Tests unitaires module par module
- [ ] Tests intégration
- [ ] Vérifier non-bloquant (toutes exceptions catchées)
- [ ] Vérifier idempotence
- [ ] Documentation mise à jour

---

## 🎨 Patterns de Code

### Pattern Service + ServiceImpl

```java
// service/OcrService.java
public interface OcrService {
    void runDocument(String documentId);
    void runType(String type);
}

// service/impl/OcrServiceImpl.java
@Slf4j
public class OcrServiceImpl implements OcrService {
    
    private final AppConfig config;
    private final TesseractApi tesseract;
    
    @Override
    public void runDocument(String documentId) {
        log.info("🔄 OCR extraction: {}", documentId);
        
        try {
            // Logique métier ici
            // Pas de throw, tout catchéé
            
        } catch (Exception e) {
            log.error("❌ OCR failed: {}", documentId, e);
            // Continue, ne stop pas le job
        }
    }
    
    @Override
    public void runType(String type) {
        // Implémentation...
    }
}
```

### Pattern Job Délégant

```java
// job/OcrJob.java
@Slf4j
public class OcrJob {
    
    private final OcrService ocrService;
    
    public OcrJob() {
        this.ocrService = new OcrServiceImpl();
    }
    
    public synchronized void runDocument(String documentId) {
        ocrService.runDocument(documentId);
    }
    
    public void run(String type) {
        ocrService.runType(type);
    }
}
```

---

## ✅ Bénéfices

1. **Cohérence** : Tous les modules suivent la même architecture
2. **Maintenabilité** : Code centralisé dans `service/impl/`
3. **Testabilité** : Interfaces mockables pour tests unitaires
4. **Évolutivité** : Facilité pour ajouter nouvelles implémentations
5. **Clarté** : Séparation responsabilités (Job → Service → Repository)
6. **Non-bloquant** : Toute exception catchée dans ServiceImpl

---

## 🚀 Ordre d'Exécution Recommandé

1. **law-pdf-ocr** (plus simple, modèle)
2. **law-ocr-json** (moyenne complexité)
3. **law-qa** (simple, rapide)
4. **law-json-config** (orchestration)
5. **law-ai** (plus complexe, dernière)

---

**Date** : 15 décembre 2025  
**Statut** : 📝 Planification complète  
**Prochaine étape** : Commencer par law-pdf-ocr
