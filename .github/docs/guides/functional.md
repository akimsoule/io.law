# Guide Fonctionnel - io.law

## Vue d'ensemble

Application pour extraire, traiter et consolider les lois et décrets du gouvernement béninois depuis le site https://sgg.gouv.bj/doc.

---

## Configuration Application

### application.yml

```yaml
spring:
  application:
    name: io.law
  
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:3306/law_db?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: ${DATABASE_USERNAME:root}
    password: ${DATABASE_PASSWORD:root}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
  
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        format_sql: true
    open-in-view: false
  
  batch:
    job:
      enabled: false  # Manuel via CLI/API
    jdbc:
      initialize-schema: always

law:
  base-url: https://sgg.gouv.bj/doc
  user-agent: Mozilla/5.0 (compatible; LawBatchBot/1.0)
  end-year: 1960
  max-number-per-year: 2000
  
  storage:
    base-path: data
    pdf-dir: pdfs
    ocr-dir: ocr
    json-dir: articles
  
  http:
    timeout: 30000
  
  ocr:
    language: fra
    dpi: 300
    quality-threshold: 0.70
  
  batch:
    chunk-size: 10
    max-threads: 20
    max-items-to-fetch-previous: 10000
  
  capacity:
    ia: 4   # Score RAM/CPU IA (16GB+)
    ocr: 2  # Score OCR (4GB+)
    ollama-url: http://localhost:11434
    ollama-models-required: gemma3n:latest
  
  groq:
    api-key: ${GROQ_API_KEY:}

quality:
  sequence-penalty: enabled   # Pénalité si numérotation non séquentielle
  dictionary-penalty: enabled # Pénalité progressive via mots non reconnus
  unrecognized-words-file: data/word_non_recognize.txt

logging:
  level:
    root: INFO
    bj.gouv.sgg: DEBUG
    org.springframework.batch: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

---

## Jobs Disponibles

### 1. fetchCurrentJob
**Objectif** : Scanner l'année courante pour détecter nouveaux documents

**Fonctionnement** :
- Génère numéros 1 à 2000 pour année courante
- Vérifie disponibilité via HEAD request
- Enregistre documents trouvés (statut FETCHED)
- Détecte plages 404 pour optimiser scans futurs

**Commande** :
```bash
java -jar law-app.jar --job=fetchCurrentJob
```

### 2. fetchPreviousJob
**Objectif** : Scanner années précédentes (1960 à année-1)

**Fonctionnement** :
- Reprend depuis dernier cursor sauvegardé
- Scan incrémental par année/numéro
- Gère plages 404 connues (skip)
- Sauvegarde cursor après chaque chunk

**Commande** :
```bash
java -jar law-app.jar --job=fetchPreviousJob
```

### 3. downloadJob
**Objectif** : Télécharger PDFs des documents détectés

**Fonctionnement** :
- Lit documents PENDING ou FETCHED
- Télécharge PDF depuis SGG
- Détecte fichiers corrompus (PNG déguisés, tronqués)
- Sauvegarde dans `data/pdfs/{type}/{docId}.pdf`
- Update statut → DOWNLOADED ou CORRUPTED

**Commande** :
```bash
java -jar law-app.jar --job=downloadJob
```

### 4. pdfToJsonJob ✅
**Objectif** : Transformer PDF → JSON structuré (OCR + IA avec fallback automatique)

**Fonctionnement** :
- Lit documents DOWNLOADED
- **Stratégie fallback automatique** :
  1. Tente extraction via **IA Ollama** (si capacité IA ≥ 4)
  2. Fallback vers **Groq API** (si Ollama échoue)
  3. Fallback vers **OCR** (Tesseract + parsing regex)
- Exécute Tesseract OCR pour texte brut
- Parse articles, métadonnées, signataires via regex
- Applique 258 corrections OCR
- Génère JSON dans `data/articles/{type}/`
- Enregistre mots non reconnus + applique pénalités confiance
- Update statut → EXTRACTED ou FAILED

**Commande** :
```bash
java -jar law-app.jar --job=pdfToJsonJob

# Mode ciblé
java -jar law-app.jar --job=pdfToJsonJob --doc=loi-2024-15

# Mode force (écrase JSON existants)
java -jar law-app.jar --job=pdfToJsonJob --doc=decret-2024-1632 --force
```

### 5. consolidateJob ✅
**Objectif** : Import JSON → MySQL

**Fonctionnement** :
- Lit documents EXTRACTED depuis `law_documents`
- Parse fichiers JSON depuis `data/articles/{type}/{docId}.json`
- Insert/Update 3 tables MySQL :
  - `consolidated_metadata` : Métadonnées document
  - `consolidated_articles` : Articles extraits
  - `consolidated_signatories` : Signataires
- Idempotent : Skip si déjà consolidé
- Gère erreurs : Marque FAILED, continue job
- Update statut → CONSOLIDATED ou FAILED

**Commande** :
```bash
java -jar law-app.jar --job=consolidateJob
```

**Résultats actuels** :
- 14 documents consolidés (78% succès)
- 299 articles insérés
- 35 signataires
- 4 documents FAILED (nécessitent investigation)

---

## Qualité & Mots Non Reconnus

**Suivi des mots non reconnus pendant extraction** :
- Fichier : `data/word_non_recognize.txt` (unicité par mot)
- Log attendu : `Recorded X new unrecognized words (total: Y)` lors de `pdfToJsonJob`
- Impact : Pénalités progressives sur score de confiance

**Commandes utiles** :
```bash
# Forcer extraction pour enregistrer les mots
java -jar law-app.jar --job=pdfToJsonJob --doc=decret-2024-1632 --force

# Vérifier le fichier
wc -l data/word_non_recognize.txt
tail -20 data/word_non_recognize.txt

# Statistiques (si disponible)
sh scripts/unrecognized_words_stats.sh
```

---

## Pipeline Complet

```bash
# 1. Démarrer MySQL
docker run -d --name mysql-law \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=law_db \
  -p 3306:3306 mysql:8.4

# 2. Scanner année courante
java -jar law-app.jar --job=fetchCurrentJob

# 3. Scanner années précédentes
java -jar law-app.jar --job=fetchPreviousJob

# 4. Télécharger PDFs
java -jar law-app.jar --job=downloadJob

# 5. Extraction PDF → JSON (OCR + IA)
java -jar law-app.jar --job=pdfToJsonJob

# 6. Consolidation BD
java -jar law-app.jar --job=consolidateJob
```

---

## Structure Fichiers

### Entrée (Site SGG)
```
https://sgg.gouv.bj/doc/loi-2024-15.pdf
https://sgg.gouv.bj/doc/decret-2024-1632.pdf
```

### Sortie (Stockage Local)
```
data/
├── pdfs/
│   ├── loi/
│   │   ├── loi-2024-15.pdf
│   │   ├── loi-2025-11.pdf
│   │   └── ...
│   └── decret/
│       ├── decret-2024-1632.pdf
│       └── ...
├── ocr/
│   ├── loi/
│   │   ├── loi-2024-15.txt
│   │   └── ...
│   └── decret/
│       └── ...
└── articles/
    ├── loi/
    │   ├── loi-2024-15.json
    │   └── ...
    └── decret/
        └── ...
```

### Format JSON (Sortie Extraction)
```json
{
  "documentId": "loi-2024-15",
  "type": "loi",
  "year": 2024,
  "number": 15,
  "title": "Loi n° 2024-15 portant...",
  "publicationDate": "2024-03-15",
  "metadata": {
    "assemblyDate": "2024-03-10",
    "promulgationDate": "2024-03-14",
    "journalOfficielRef": "N° 12 du 15 mars 2024"
  },
  "articles": [
    {
      "number": 1,
      "content": "La présente loi porte...",
      "type": "standard"
    },
    {
      "number": 2,
      "content": "Article 2 content...",
      "type": "standard"
    }
  ],
  "signatories": [
    {
      "name": "Patrice TALON",
      "title": "Président de la République",
      "signatureType": "promulgation"
    }
  ],
  "extractionMetadata": {
    "extractionMethod": "ocr",
    "confidence": 0.85,
    "extractionDate": "2025-12-08T10:30:00Z"
  }
}
```

---

## Consultation Base de Données

### Statistiques Globales
```sql
SELECT 
    status,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM law_documents), 2) as percentage
FROM law_documents
GROUP BY status
ORDER BY count DESC;
```

### Documents par Année
```sql
SELECT 
    document_year,
    type,
    COUNT(*) as count
FROM law_documents
WHERE status = 'CONSOLIDATED'
GROUP BY document_year, type
ORDER BY document_year DESC, type;
```

### Documents Corrompus
```sql
SELECT 
    document_id,
    type,
    document_year,
    number
FROM law_documents
WHERE status = 'CORRUPTED'
ORDER BY document_year DESC, number;
```

### Progression Extraction
```sql
SELECT 
    'FETCHED' as status, COUNT(*) as count FROM law_documents WHERE status = 'FETCHED'
UNION ALL
SELECT 'DOWNLOADED', COUNT(*) FROM law_documents WHERE status = 'DOWNLOADED'
UNION ALL
SELECT 'EXTRACTED', COUNT(*) FROM law_documents WHERE status = 'EXTRACTED'
UNION ALL
SELECT 'CONSOLIDATED', COUNT(*) FROM law_documents WHERE status = 'CONSOLIDATED';
```

### Documents Consolidés
```sql
-- Liste documents consolidés
SELECT documentId, totalArticles, extractionConfidence
FROM consolidated_metadata
ORDER BY documentYear DESC, documentNumber DESC;

-- Compter articles par document
SELECT documentId, COUNT(*) as nb_articles
FROM consolidated_articles
GROUP BY documentId
ORDER BY nb_articles DESC;

-- Signataires distincts
SELECT DISTINCT role, name, COUNT(*) as nb_documents
FROM consolidated_signatories
GROUP BY role, name
ORDER BY nb_documents DESC;

-- Recherche texte dans articles
SELECT documentId, articleIndex, LEFT(content, 100) as preview
FROM consolidated_articles
WHERE content LIKE '%recherche%'
LIMIT 10;
```

---

## API REST (⏳ À venir)

### Endpoints Prévus

#### Documents
- `GET /api/documents` : Liste tous les documents
- `GET /api/documents/{id}` : Détails d'un document
- `GET /api/documents?year={year}&type={type}` : Filtrer par année/type
- `GET /api/documents/{id}/pdf` : Télécharger PDF
- `GET /api/documents/{id}/json` : Télécharger JSON extrait

#### Articles
- `GET /api/articles?documentId={id}` : Articles d'un document
- `GET /api/articles/search?q={query}` : Recherche dans articles

#### Statistiques
- `GET /api/stats/global` : Statistiques globales
- `GET /api/stats/by-year` : Répartition par année
- `GET /api/stats/extraction` : Progression extraction

#### Jobs
- `POST /api/jobs/fetch-current` : Lancer fetchCurrentJob
- `POST /api/jobs/fetch-previous` : Lancer fetchPreviousJob
- `POST /api/jobs/download` : Lancer downloadJob
- `GET /api/jobs/{id}/status` : Statut d'un job

---

## Monitoring

### Métriques Clés

**Extraction** :
- Taux succès extraction : 80% (38/47 fichiers) ✅
- Taux succès consolidation : 78% (14/18 documents) ✅
- Qualité haute confiance : ~13% (≥0.7)
- Documents corrompus : ~5-10%

**Performance** :
- Fetch : ~100 documents/minute
- Download : ~50 PDFs/minute
- OCR : ~5-10 documents/minute
- Extraction JSON : ~20-30 documents/minute
- Consolidation : ~10-15 documents/minute

**Base de Données** :
- Documents totaux : 19 (14 CONSOLIDATED + 4 FAILED + 1 FETCHED)
- Articles consolidés : 299
- Signataires : 35
- Taille base : ~5MB (données actuelles)

---

## Dépannage

### Job Bloqué
```bash
# Vérifier jobs en cours
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "SELECT * FROM BATCH_JOB_EXECUTION WHERE STATUS = 'STARTED';"

# Arrêter job manuellement
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "UPDATE BATCH_JOB_EXECUTION SET STATUS = 'FAILED' WHERE JOB_EXECUTION_ID = {id};"
```

### Réinitialiser Extraction
```bash
# Reset statut documents
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "UPDATE law_documents SET status = 'DOWNLOADED' WHERE status = 'EXTRACTED';"

# Supprimer fichiers OCR/JSON
rm -rf data/ocr/* data/articles/*
```

### Réinitialiser Consolidation
```bash
# Reset statut documents consolidés
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "UPDATE law_documents SET status = 'EXTRACTED' WHERE status = 'CONSOLIDATED';"

# Vider tables consolidées
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "TRUNCATE TABLE consolidated_articles; \
   TRUNCATE TABLE consolidated_metadata; \
   TRUNCATE TABLE consolidated_signatories;"

# Analyser documents FAILED
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "SELECT document_id, type, document_year, number \
   FROM law_documents WHERE status = 'FAILED';"
```

### Logs
```bash
# Logs application
tail -f logs/law-app.log

# Logs MySQL
docker logs mysql-law

# Logs job spécifique
grep "downloadJob" logs/law-app.log
```

---

## Maintenance

### Backup Complet
```bash
# Backup MySQL
docker exec mysql-law mysqldump -u root -proot law_db > backup-$(date +%Y%m%d).sql

# Backup fichiers
tar -czf data-backup-$(date +%Y%m%d).tar.gz data/
```

### Nettoyage
```bash
# Supprimer documents corrompus
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "DELETE FROM law_documents WHERE status = 'CORRUPTED';"

# Supprimer PDFs corrompus
find data/pdfs -type f -size 0 -delete
```

### Mise à jour Corrections OCR
```bash
# Ajouter correction
echo '"erreur OCR,correction"' >> law-tojson/law-ocr-json/src/main/resources/corrections.csv

# Recompiler module
mvn clean install -pl law-tojson/law-ocr-json

# Re-tester
mvn -pl law-tojson/law-ocr-json test
```
