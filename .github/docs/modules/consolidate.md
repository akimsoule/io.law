# law-consolidate

Module de consolidation des données JSON extraites vers MySQL.

## Objectif

Importer les articles, métadonnées et signataires extraits par `law-ocr-json` depuis les fichiers JSON vers la base de données MySQL.

## Architecture

```
law-consolidate/
├── model/                    # Entités JPA consolidées
│   ├── ConsolidatedArticle.java
│   ├── ConsolidatedMetadata.java
│   └── ConsolidatedSignatory.java
├── repository/               # Repositories JPA
│   ├── ConsolidatedArticleRepository.java
│   ├── ConsolidatedMetadataRepository.java
│   └── ConsolidatedSignatoryRepository.java
├── service/                  # Service de consolidation
│   └── ConsolidationService.java
├── batch/                    # Composants Spring Batch
│   ├── reader/
│   │   └── JsonFileItemReader.java
│   ├── processor/
│   │   └── ConsolidationProcessor.java
│   └── writer/
│       └── ConsolidationWriter.java
└── config/                   # Configuration job
    └── ConsolidateJobConfiguration.java
```

## Flux de Données

```
law_documents (EXTRACTED)
       ↓
JsonFileItemReader
       ↓ LawDocument
ConsolidationProcessor
  - Charge JSON depuis data/articles/{type}/{docId}.json
  - Parse via ConsolidationService
  - Insère ConsolidatedArticle, ConsolidatedMetadata, ConsolidatedSignatory
  - Update status → CONSOLIDATED
       ↓ LawDocument (CONSOLIDATED)
ConsolidationWriter
  - Sauvegarde status en BD
```

## Entités Consolidées

### ConsolidatedArticle

Stocke les articles extraits avec :
- `documentId` + `articleIndex` : Clé unique
- `content` : Texte complet de l'article
- `extractionConfidence`, `extractionMethod` : Métadonnées qualité
- Index : documentId, documentType, documentYear

### ConsolidatedMetadata

Stocke les métadonnées d'un document :
- `documentId` : Clé unique (1 metadata par document)
- Titre, date promulgation, ville, JO ref, etc.
- `totalArticles` : Nombre d'articles
- Index : documentType, documentYear, promulgationDate

### ConsolidatedSignatory

Stocke les signataires :
- `documentId` + `signatoryOrder` : Clé unique
- `role`, `name` : Informations signataire
- `mandateStart`, `mandateEnd` : Dates mandat (optionnelles)
- Index : documentId, role, name

## Job Spring Batch

### consolidateJob

**Commande** :
```bash
java -jar law-app.jar --job=consolidateJob
```

**Fonctionnement** :
1. **Reader** : Lit documents status EXTRACTED
2. **Processor** : Parse JSON → Insère en BD
3. **Writer** : Update status → CONSOLIDATED

**Configuration** :
- Chunk size : 10 documents
- Transaction : Par chunk (rollback si erreur)
- Idempotent : Re-run safe (UPDATE si existe)

## Service de Consolidation

### ConsolidationService

**Méthodes principales** :
- `consolidateDocument(LawDocument)` : Parse JSON → BD
- `isDocumentConsolidated(String)` : Vérifie si déjà consolidé
- `deleteConsolidatedDocument(String)` : Supprime données (re-consolidation)

**Gestion idempotence** :
```java
// Récupérer ou créer metadata
ConsolidatedMetadata metadata = metadataRepository.findByDocumentId(docId)
    .orElse(ConsolidatedMetadata.builder()
        .documentId(docId)
        .build());
        
// Mapper champs
metadata.set...();

// Sauvegarder (INSERT ou UPDATE automatique)
metadataRepository.save(metadata);
```

## Format JSON Attendu

```json
{
  "_metadata": {
    "confidence": 0.95,
    "source": "OCR:PROGRAMMATIC",
    "timestamp": "2025-12-07T16:58:19.582425Z"
  },
  "documentId": "loi-2024-15",
  "type": "loi",
  "year": 2024,
  "number": 15,
  "promulgationDate": "2024-04-29",
  "promulgationCity": "Cotonou",
  "articles": [
    {"index": 1, "content": "Article 1er : ..."},
    {"index": 2, "content": "Article 2 : ..."}
  ],
  "signatories": [
    {
      "role": "Président de la République",
      "name": "Patrice TALON",
      "mandateStart": "2021-04-06",
      "mandateEnd": null
    }
  ]
}
```

## Requêtes SQL Utiles

### Statistiques consolidation

```sql
-- Compter documents consolidés par type
SELECT 
    documentType, 
    COUNT(*) as total,
    AVG(extractionConfidence) as avg_confidence
FROM consolidated_metadata
GROUP BY documentType;

-- Trouver documents haute confiance (>= 0.8)
SELECT documentId, extractionConfidence, totalArticles
FROM consolidated_metadata
WHERE extractionConfidence >= 0.8
ORDER BY documentYear DESC, documentNumber DESC;

-- Compter articles par document
SELECT 
    documentId,
    COUNT(*) as total_articles,
    AVG(extractionConfidence) as avg_confidence
FROM consolidated_articles
GROUP BY documentId
ORDER BY documentId;
```

### Recherche signataires

```sql
-- Trouver tous les documents signés par une personne
SELECT DISTINCT documentId, documentType, documentYear
FROM consolidated_signatories
WHERE name LIKE '%TALON%'
ORDER BY documentYear DESC;

-- Compter documents par rôle signataire
SELECT role, COUNT(DISTINCT documentId) as documents_signed
FROM consolidated_signatories
GROUP BY role
ORDER BY documents_signed DESC;
```

### Recherche articles

```sql
-- Recherche full-text dans articles
SELECT 
    ca.documentId,
    ca.articleIndex,
    SUBSTRING(ca.content, 1, 200) as preview
FROM consolidated_articles ca
WHERE ca.content LIKE '%usure%'
ORDER BY ca.documentYear DESC, ca.articleIndex;

-- Articles par année et type
SELECT 
    cm.documentYear,
    cm.documentType,
    COUNT(DISTINCT ca.documentId) as total_documents,
    COUNT(ca.id) as total_articles
FROM consolidated_metadata cm
JOIN consolidated_articles ca ON cm.documentId = ca.documentId
GROUP BY cm.documentYear, cm.documentType
ORDER BY cm.documentYear DESC;
```

## Dépannage

### Documents non consolidés

```sql
-- Trouver documents EXTRACTED non consolidés
SELECT ld.documentId, ld.status
FROM law_documents ld
WHERE ld.status = 'EXTRACTED'
  AND NOT EXISTS (
    SELECT 1 FROM consolidated_metadata cm
    WHERE cm.documentId = ld.documentId
  );
```

### Re-consolidation

Si un document doit être re-consolidé :

```java
// Via service (supprime tout)
consolidationService.deleteConsolidatedDocument("loi-2024-15");

// Relancer job
java -jar law-app.jar --job=consolidateJob
```

### Logs

```bash
# Logs consolidation
grep "Consolidation" logs/law-app.log

# Erreurs consolidation
grep "❌.*consolidat" logs/law-app.log
```

## Tests

### Tests unitaires

```bash
mvn test -pl law-consolidate
```

### Tests d'intégration

```bash
mvn verify -pl law-consolidate
```

## Prochaines Étapes

- ✅ Entités JPA (Article, Metadata, Signatory)
- ✅ Repositories JPA
- ✅ Service de consolidation
- ✅ Composants Spring Batch (Reader, Processor, Writer)
- ✅ Configuration job
- ⏳ Tests unitaires
- ⏳ Tests d'intégration
- ⏳ Intégration dans law-app
