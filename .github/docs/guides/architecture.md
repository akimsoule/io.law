# Architecture - io.law

## Vue d'ensemble

Application Spring Batch modulaire pour extraire, traiter et consolider les lois/décrets depuis https://sgg.gouv.bj/doc.

### Technologies
- **Java**, **Spring Boot 3.2.0** + Spring Batch
- **Maven Multi-Modules** (11 modules)
- **PDFBox** (extraction PDF), **Tesseract OCR** (JavaCPP)
- **MySQL 8.4** (Docker), **Ollama/Groq** (parsing IA optionnel)
 - **Qualité extraction** : pénalités séquence d'articles, dictionnaire FR (~336k mots), suivi des mots non reconnus

---

## Structure Multi-Modules

```
io.law/
├── law-common/          # Socle (models, repos, exceptions, config)
├── law-fetch/           # Récupération métadonnées (2 jobs)
├── law-download/        # Téléchargement PDFs
├── law-tojson/          # PDF → JSON (3 sous-modules)
│   ├── law-pdf-ocr/        # Extraction OCR
│   ├── law-ocr-json/       # Parsing OCR → JSON ✅
│   ├── law-json-config/    # Config commune ✅
│   └── (law-tojson-app)/   # Orchestration (⏳ TODO)
├── law-consolidate/     # Consolidation BD
└── law-app/             # API REST + CLI + orchestration
```

---

## Modules Détaillés

### law-common (Socle)
**Responsabilité** : Composants partagés par tous les modules

**Contenu** :
- `model/` : Entités JPA (`LawDocument`)
- `repository/` : Repositories JPA (`LawDocumentRepository`)
- `exception/` : Exceptions métier (21 exceptions spécifiques)
- `config/` : Configuration Spring (`LawProperties`, `GsonConfig`, `DatabaseConfig`)
- `service/` : Services (`FileStorageService`, `DocumentStatusManager`)
- `util/` : Utilitaires (`DateUtils`, `StringUtils`, `ValidationUtils`)

### law-fetch (Récupération)
**Responsabilité** : Scanner le site SGG et détecter documents disponibles

**Jobs** :
1. `fetchCurrentJob` : Scan année courante (1-2000)
2. `fetchPreviousJob` : Scan années 1960 à année-1 avec cursor

**Composants** :
- `CurrentYearLawDocumentReader` : Génère documents année courante
- `PreviousYearsLawDocumentReader` : Lit depuis cursor
- `FetchProcessor` : HEAD requests HTTP + détection 404
- `FetchWriter` : Sauvegarde résultats + cursor

### law-download (Téléchargement)
**Responsabilité** : Télécharger PDFs depuis SGG

**Job** : `downloadJob`

**Composants** :
- `DownloadReader` : Lit documents PENDING/FETCHED
- `DownloadProcessor` : Télécharge PDF + détecte corruptions
- `DownloadWriter` : Sauvegarde PDF + update statut

### law-tojson (Transformation)
**Responsabilité** : Extraire contenu structuré des PDFs

#### law-pdf-ocr
- Extraction OCR via Tesseract
- Génère fichiers `.txt`

#### law-ocr-json ✅
- Parse OCR → JSON structuré
- 258 corrections OCR
- Extraction articles, métadonnées, signataires
- 70 tests (69 pass, 1 skip)
 - Enregistre les mots OCR non reconnus dans `data/word_non_recognize.txt`
 - Calcule une pénalité progressive de confiance selon le taux et le volume de mots non reconnus

#### law-json-config ✅
- Modèles JSON partagés (`Article`, `Signatory`, `DocumentMetadata`)
- Configuration commune

#### law-tojson-app (⏳ TODO)
- Orchestration des 3 extracteurs
- Stratégie : OCR → IA si échec

### law-consolidate ✅
**Responsabilité** : Import JSON → MySQL

**Job** : `consolidateJob`

**Composants** :
- `ConsolidationService` : Parse JSON + persist BD (Gson)
- `JsonFileItemReader` : Lit documents EXTRACTED
- `ConsolidationProcessor` : Validation + consolidation
- `ConsolidationWriter` : Update statut → CONSOLIDATED
- 3 entités JPA : `ConsolidatedArticle`, `ConsolidatedMetadata`, `ConsolidatedSignatory`
- 3 repositories avec requêtes métier

### law-app
**Responsabilité** : Orchestration + API REST

**Fonctionnalités** :
- CLI pour lancer jobs
- API REST pour consultation
- Swagger documentation

---

## Flux de Données

```
┌─────────────┐
│  SGG Site   │
└──────┬──────┘
       │
       ▼
┌─────────────┐    ┌──────────────┐
│  law-fetch  │───▶│    MySQL     │
└─────────────┘    └──────┬───────┘
                          │
                          ▼
                   ┌──────────────┐
                   │ law-download │
                   └──────┬───────┘
                          │
                          ▼
                   ┌──────────────┐
                   │  law-tojson  │
                   │  (OCR/IA)    │
                   └──────┬───────┘
                          │
                          │
                          ▼
                   ┌─────────────────────────────────────────────┐
                   │  Qualité OCR/JSON                           │
                   │  - Séquence articles (gaps/doublons/ordre)  │
                   │  - Dictionnaire FR & mots non reconnus      │
                   │  - Fichier: data/word_non_recognize.txt     │
                   └─────────────────────────────────────────────┘
                          ▼
                   ┌──────────────┐
                   │law-consolidate│
                   └──────┬───────┘
                          │
                          ▼
                   ┌──────────────┐
                   │   law-app    │
                   │  (API REST)  │
                   └──────────────┘
```

---

## Statuts Documents

```java
PENDING      // Créé, pas traité
FETCHED      // Métadonnées OK (HEAD 200)
DOWNLOADED   // PDF téléchargé
EXTRACTED    // OCR effectué (.txt créé)
CONSOLIDATED // Données en BD MySQL
FAILED       // Erreur générique
CORRUPTED    // PDF corrompu (PNG, tronqué, etc.)
// (interne au calcul de confiance)
// QUALIFIED : Confiance calculée et pénalités appliquées
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
 - **Mots non reconnus** : 53 mots uniques enregistrés (initial) via `pdfToJsonJob --force`
- **Build** : ✅ SUCCESS
- **Données MySQL** :
  - 14 documents consolidés
  - 299 articles extraits
  - 35 signataires

### 🚀 Prochaines Étapes

1. **Tests law-consolidate** : Tests unitaires + intégration pour ConsolidationService
2. **Analyser 4 FAILED** : Documents échoués lors de la consolidation
3. **Améliorer extraction OCR** : Analyser 9 fichiers échouant → Objectif 90%+
4. **law-tojson-app** : Orchestration OCR → IA (fallback)
5. **law-app** : API REST + Swagger pour consultation
6. **Pipeline automatique** : Orchestration complète fetch → consolidate
7. **Boucle qualité** : Exploiter `data/word_non_recognize.txt` pour corriger CSV et améliorer les patterns
