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

### Structure des Modules

#### 1. **law-app** (Application principale)
- **Type** : Application Spring Boot principale
- **Rôle** : Point d'entrée de l'application, orchestration des pipelines
- **Dépendances** : law-fetch, law-download, law-json-config

#### 2. **law-common**
- **Type** : JAR de composants partagés
- **Rôle** : Entités, repositories, services communs, configuration Spring Batch
- **Utilisé par** : Tous les autres modules

#### 3. **law-fetch**
- **Type** : JAR de configuration Spring Batch
- **Rôle** : Extraction de la liste des documents depuis https://sgg.gouv.bj/doc
- **Configuration** : Job, Steps, Readers, Writers pour le scraping web

#### 4. **law-download**
- **Type** : JAR de configuration Spring Batch
- **Rôle** : Téléchargement des PDFs depuis les URLs extraites
- **Configuration** : Job, Steps, Tasklet pour le téléchargement

#### 5. **law-json-config**
- **Type** : JAR de configuration Spring Batch orchestrateur
- **Rôle** : Orchestration de la conversion PDF → JSON
- **Dépendances actuelles** : law-ocr-json, law-pdf-ocr
- **Dépendances futures** : law-ai, law-qa (pour enrichissement et validation)

#### 6. **law-ocr-json** (sous law-to-json/)
- **Type** : JAR de configuration Spring Batch
- **Rôle** : Extraction du texte OCR et structuration en JSON
- **Configuration** : Job, Steps pour lecture OCR et génération JSON

#### 7. **law-pdf-ocr** (sous law-to-json/)
- **Type** : JAR de configuration Spring Batch
- **Rôle** : Extraction OCR depuis les PDFs scannés
- **Technologie** : Tesseract OCR via JavaCPP
- **Configuration** : Job, Steps pour OCR des PDFs

#### 8. **law-ai** (sous law-to-json/)
- **Type** : JAR de services POJO
- **Rôle** : Services IA pour parsing via Ollama/Groq
- **Implémentation** : Strategy pattern (IAProvider, IAProviderFactory)
- **Dépendances** : gson + slf4j uniquement (pas de Spring)

#### 9. **law-qa** (sous law-to-json/)
- **Type** : JAR de services POJO
- **Rôle** : Validation qualité JSON et OCR
- **Services** : ValidationService, JsonQualityService, OcrQualityService
- **Dépendances** : gson + slf4j uniquement (pas de Spring)

#### 10. **law-consolidate**
- **Type** : JAR de configuration Spring Batch
- **Rôle** : Consolidation et déduplication des données finales
- **Configuration** : Job, Steps pour fusion et nettoyage

### Graphe de Dépendances

```
law-app (Spring Boot)
├── law-common
├── law-fetch → law-common
├── law-download → law-common
├── law-json-config → law-common
│   ├── law-pdf-ocr → law-common
│   ├── law-ocr-json → law-common
│   ├── law-ai (services POJO - gson + slf4j uniquement)
│   └── law-qa (services POJO - gson + slf4j uniquement)
└── law-consolidate → law-common
```

**Note :** law-ai et law-qa sont des modules de services POJO simples sans Spring Batch, utilisables comme librairies réutilisables.

### Principes Architecturaux

1. **Séparation des responsabilités** : Chaque module a un rôle unique et bien défini
2. **Configuration externalisée** : Jobs Spring Batch définis comme beans dans modules batch
3. **Réutilisation** : law-common contient code partagé; law-ai et law-qa sont des services POJO réutilisables
4. **Moindre dépendance** : law-ai et law-qa n'ont pas de dépendances Spring (gson + slf4j uniquement)
5. **Évolutivité** : Architecture modulaire permettant ajout facile de nouveaux modules

law-app reste le point d'entrée unique, orchestrant les modules batch suivants : 
- law-fetch
- law-download
- law-json-config (qui orchestre à son tour law-pdf-ocr, law-ocr-json, law-ai, law-qa)
- law-consolidate

Il doit être capable de lancer les jobs individuellement ou en pipeline complet via des paramètres.
Les jobs suivants doivent pouvoir être lancés indépendamment :

**Jobs de Fetch (law-fetch)** :
- fetchCurrentJob --type=loi
- fetchCurrentJob --type=decret
- fetchCurrentJob --documentId=loi-2025-001
- fetchCurrentJob --documentId=decret-2025-015

- fetchPreviousJob --type=loi
- fetchPreviousJob --type=decret
- fetchPreviousJob --documentId=loi-2022-123
- fetchPreviousJob --documentId=decret-2021-045

**Jobs de Download (law-download)** :
- downloadJob --type=loi
- downloadJob --type=decret
- downloadJob --documentId=loi-2025-001
- downloadJob --documentId=decret-2025-015

**Jobs de Conversion (law-to-json)** :
- ocrJob --type=loi                    # PDF → OCR (law-pdf-ocr)
- ocrJob --type=decret
- ocrJob --documentId=loi-2025-001

- ocrJsonJob --type=loi                # OCR → JSON (law-ocr-json)
- ocrJsonJob --type=decret
- ocrJsonJob --documentId=loi-2025-001

- jsonConversionJob --type=loi         # Pipeline complet: PDF → OCR → JSON (law-json-config)
- jsonConversionJob --type=decret
- jsonConversionJob --documentId=loi-2025-001

**Jobs de Consolidation (law-consolidate)** :
- consolidateJob --type=loi
- consolidateJob --type=decret
- consolidateJob --documentId=loi-2025-001

**Pipeline Complet** :
- --pipeline=fullPipeline --type=loi   # Exécute les 6 jobs dans l'ordre
- --pipeline=fullPipeline --type=decret --documentId=decret-2024-50

**Orchestration Continue** :
- --job=orchestrate --type=loi [--skip-fetch-daily=true|false]
