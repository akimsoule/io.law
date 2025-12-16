# fixJob - Correction Automatique & Amélioration Continue

## Description

Le job `fixJob` analyse l'ensemble des documents de la base de données pour détecter et corriger automatiquement les problèmes dans le pipeline de traitement. Son objectif : **amélioration continue sans blocage**.

## 🎯 Objectifs

1. **Détecter les blocages** : Documents coincés dans un statut
2. **Corriger les erreurs** : Fichiers manquants, corrompus
3. **Améliorer la qualité** : Re-extraction documents à faible confiance
4. **Non-bloquant** : Aucune exception ne stoppe le job
5. **Amélioration continue** : Exécution régulière pour maintenance proactive

---

## Architecture

### Flux d'Exécution

```
┌─────────────────────────┐
│  AllDocumentsReader     │  Lit TOUS les documents (tous statuts)
└───────────┬─────────────┘
            │ Pagination : 1000 docs/page
            ▼
┌─────────────────────────┐
│  FixProcessor           │  Pour chaque document :
│  (FixOrchestrator)      │  1. Détecte tous les problèmes
└───────────┬─────────────┘  2. Priorise par sévérité
            │ Chunk : 10      3. Applique corrections
            ▼
┌─────────────────────────┐
│  StatusIssueDetector    │  → Documents bloqués (PENDING, FETCHED...)
│  FileIssueDetector      │  → Fichiers manquants/corrompus
│  QualityIssueDetector   │  → Confiance faible, séquence, mots non reconnus
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│  StatusFixService       │  → Réinitialise statut
│  FileFixService         │  → Supprime/recréé fichiers
│  QualityFixService      │  → Force re-extraction
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│  FixWriter              │  Finalise batch (corrections déjà appliquées)
└─────────────────────────┘
```

---

## 📋 Types de Problèmes

### 1. Problèmes de Statut

**Objectif** : Détecter documents n'ayant pas progressé dans le pipeline.

| Type | Statut Actuel | Sévérité | Auto-fixable | Action |
|------|---------------|----------|--------------|--------|
| `STUCK_IN_PENDING` | PENDING | HIGH | ❌ | Signal : Relancer fetchCurrentJob |
| `STUCK_IN_FETCHED` | FETCHED | HIGH | ❌ | Signal : Relancer downloadJob |
| `STUCK_IN_DOWNLOADED` | DOWNLOADED | MEDIUM | ❌ | Signal : Relancer pdfToJsonJob |
| `STUCK_IN_EXTRACTED` | EXTRACTED | MEDIUM | ❌ | Signal : Relancer consolidateJob |

**Note** : Ces problèmes ne sont PAS corrigés automatiquement (ils signalent juste un job à relancer).

---

### 2. Problèmes de Fichiers

**Objectif** : Détecter incohérences entre statut et fichiers disque.

| Type | Description | Sévérité | Auto-fixable | Correction |
|------|-------------|----------|--------------|------------|
| `MISSING_PDF` | PDF absent, statut=DOWNLOADED+ | CRITICAL | ✅ | Réinitialise → FETCHED |
| `MISSING_OCR` | OCR absent, statut=EXTRACTED+ | HIGH | ✅ | Réinitialise → DOWNLOADED |
| `MISSING_JSON` | JSON absent, statut=EXTRACTED+ | HIGH | ✅ | Réinitialise → DOWNLOADED |
| `CORRUPTED_PDF` | PDF corrompu (PNG déguisé, tronqué) | CRITICAL | ✅ | Supprime PDF + réinitialise → FETCHED |

**Stratégie** : Réinitialiser le statut au stade juste avant la création du fichier manquant, permettant au pipeline de re-générer le fichier.

---

### 3. Problèmes de Qualité

**Objectif** : Améliorer qualité extraction en forçant re-traitement.

| Type | Description | Seuil | Sévérité | Auto-fixable | Correction |
|------|-------------|-------|----------|--------------|------------|
| `LOW_CONFIDENCE` | Confiance extraction < 30% | 0.3 | HIGH | ✅ | Réinitialise → DOWNLOADED |
| `SEQUENCE_ISSUES` | Gaps/duplicates/inversions articles | > 0 | MEDIUM | ✅ | Réinitialise → DOWNLOADED |
| `HIGH_UNRECOGNIZED_WORDS` | Taux mots non reconnus > 50% | 0.5 | MEDIUM | ✅ | Réinitialise → DOWNLOADED |
| `MISSING_ARTICLES` | 0 articles extraits | 0 | HIGH | ✅ | Réinitialise → DOWNLOADED |

**Stratégie** : Forcer re-extraction (OCR/IA) en réinitialisant à DOWNLOADED. Les corrections CSV et dictionnaire améliorent progressivement les résultats.

---

### 4. Problèmes Réseau

**Objectif** : Signaler problèmes externes nécessitant intervention manuelle.

| Type | Description | Sévérité | Auto-fixable | Action |
|------|-------------|----------|--------------|--------|
| `URL_NOT_FOUND_404` | URL SGG retourne 404 | LOW | ❌ | Document inexistant sur SGG |
| `DOWNLOAD_TIMEOUT` | Timeout téléchargement récurrent | MEDIUM | ❌ | Vérifier connexion réseau |

**Note** : Non auto-fixables, nécessitent intervention manuelle ou investigation.

---

## 🔧 Détection

### FileIssueDetector

Vérifie cohérence statut ↔ fichiers disque :

```java
// Pour status=DOWNLOADED ou supérieur
if (!fileStorageService.pdfExists(doc.getType(), doc.getDocumentId())) {
    issues.add(Issue.MISSING_PDF); // CRITICAL
}

// Pour status=EXTRACTED ou supérieur
if (!fileStorageService.ocrExists(doc.getType(), doc.getDocumentId())) {
    issues.add(Issue.MISSING_OCR); // HIGH
}
if (!fileStorageService.jsonExists(doc.getType(), doc.getDocumentId())) {
    issues.add(Issue.MISSING_JSON); // HIGH
}
```

### QualityIssueDetector

Parse JSON `_metadata` pour analyser qualité :

```java
// Charge JSON depuis data/articles/{type}/{docId}.json
JsonObject metadata = jsonRoot.getAsJsonObject("_metadata");

// Confiance < 0.3
if (metadata.get("confidence").getAsDouble() < 0.3) {
    issues.add(Issue.LOW_CONFIDENCE); // HIGH
}

// Séquence articles (gaps, duplicates, out-of-order)
if (metadata.has("sequenceIssues") && metadata.get("sequenceIssues").getAsInt() > 0) {
    issues.add(Issue.SEQUENCE_ISSUES); // MEDIUM
}

// Mots non reconnus > 50%
if (metadata.has("unrecognizedWordsRate") && metadata.get("unrecognizedWordsRate").getAsDouble() > 0.5) {
    issues.add(Issue.HIGH_UNRECOGNIZED_WORDS); // MEDIUM
}

// 0 articles extraits
JsonArray articles = jsonRoot.getAsJsonArray("articles");
if (articles.size() == 0) {
    issues.add(Issue.MISSING_ARTICLES); // HIGH
}
```

### StatusIssueDetector

Signale documents non-finaux (ne nécessitant pas correction, juste relance job) :

```java
switch (doc.getStatus()) {
    case PENDING -> issues.add(Issue.STUCK_IN_PENDING);
    case FETCHED -> issues.add(Issue.STUCK_IN_FETCHED);
    case DOWNLOADED -> issues.add(Issue.STUCK_IN_DOWNLOADED);
    case EXTRACTED -> issues.add(Issue.STUCK_IN_EXTRACTED);
}
```

---

## 🔄 Correction

### Stratégie Générale

**Principe** : Réinitialiser le statut au stade juste avant le problème, permettant au pipeline de re-traiter le document.

```
CONSOLIDATED → EXTRACTED (si problème consolidation)
EXTRACTED → DOWNLOADED (si problème extraction/qualité)
DOWNLOADED → FETCHED (si problème PDF manquant/corrompu)
FETCHED → PENDING (si problème métadonnées)
```

### StatusFixService

Réinitialise statut pour déblocage :

```java
private ProcessingStatus getPreviousStatus(ProcessingStatus current) {
    return switch (current) {
        case FETCHED -> ProcessingStatus.PENDING;
        case DOWNLOADED -> ProcessingStatus.FETCHED;
        case EXTRACTED -> ProcessingStatus.DOWNLOADED;
        case CONSOLIDATED -> ProcessingStatus.EXTRACTED;
        default -> current; // PENDING, FAILED, CORRUPTED inchangés
    };
}
```

### FileFixService

Gère fichiers manquants/corrompus :

```java
switch (issue.getType()) {
    case MISSING_PDF -> {
        // Reset à FETCHED pour re-download
        document.setStatus(ProcessingStatus.FETCHED);
        repository.save(document);
    }
    case MISSING_OCR, MISSING_JSON -> {
        // Reset à DOWNLOADED pour re-extraction
        document.setStatus(ProcessingStatus.DOWNLOADED);
        repository.save(document);
    }
    case CORRUPTED_PDF -> {
        // Supprime PDF + reset à FETCHED
        Path pdfPath = fileStorageService.pdfPath(document.getType(), document.getDocumentId());
        Files.deleteIfExists(pdfPath);
        document.setStatus(ProcessingStatus.FETCHED);
        repository.save(document);
    }
}
```

### QualityFixService

Force re-extraction pour améliorer qualité :

```java
// Tous les problèmes qualité → reset à DOWNLOADED
document.setStatus(ProcessingStatus.DOWNLOADED);
repository.save(document);

// Logs suggestion amélioration
log.info("💡 Suggestion: Vérifier corrections.csv et word_non_recognize.txt pour {}", documentId);
```

---

## 🎯 Priorisation

Les problèmes sont triés par **sévérité** avant correction :

```java
List<Issue> sortedIssues = allIssues.stream()
    .sorted(Comparator.comparing(Issue::getSeverity).reversed())
    .toList();
```

**Ordre traitement** :
1. 🔴 **CRITICAL** : MISSING_PDF, CORRUPTED_PDF (bloquants)
2. 🟠 **HIGH** : MISSING_OCR, MISSING_JSON, LOW_CONFIDENCE, MISSING_ARTICLES
3. 🟡 **MEDIUM** : SEQUENCE_ISSUES, HIGH_UNRECOGNIZED_WORDS, STUCK_IN_DOWNLOADED/EXTRACTED
4. 🟢 **LOW** : URL_NOT_FOUND_404

---

## 📊 Logs & Statistiques

### Logs par Document

```
🔍 [loi-2024-15] Analyse document (status=DOWNLOADED)
📋 [loi-2024-15] 2 problème(s) détecté(s):
   - MISSING_OCR (HIGH) : Fichier OCR manquant
   - LOW_CONFIDENCE (HIGH) : Confiance extraction : 0.24
🔧 [loi-2024-15] Tentative correction: MISSING_OCR
✅ [loi-2024-15] Corrigé: MISSING_OCR - Réinitialisé à DOWNLOADED
🔧 [loi-2024-15] Tentative correction: LOW_CONFIDENCE
✅ [loi-2024-15] Corrigé: LOW_CONFIDENCE - Réinitialisé à DOWNLOADED pour re-extraction
📊 [loi-2024-15] Corrections: 2 succès, 0 échecs, 0 ignorés
```

### Statistiques Globales

Au démarrage du job :

```
📄 1234 documents à analyser
   PENDING : 50 documents
   FETCHED : 120 documents
   DOWNLOADED : 300 documents
   EXTRACTED : 450 documents
   CONSOLIDATED : 314 documents
```

À la fin du job :

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 STATISTIQUES GLOBALES fixJob
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Documents analysés : 1234
Problèmes détectés : 237
   ✅ Corrigés automatiquement : 189 (79.7%)
   ❌ Échecs correction : 12 (5.1%)
   ⏭️  Ignorés (non auto-fixables) : 36 (15.2%)

Répartition par type :
   MISSING_OCR : 45 corrigés
   LOW_CONFIDENCE : 38 corrigés
   MISSING_JSON : 32 corrigés
   SEQUENCE_ISSUES : 28 corrigés
   HIGH_UNRECOGNIZED_WORDS : 24 corrigés
   MISSING_PDF : 12 corrigés
   CORRUPTED_PDF : 10 corrigés
   STUCK_IN_DOWNLOADED : 36 signalés (relancer pdfToJsonJob)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 🚀 Usage

### Exécution Manuelle

```bash
# Via JAR
java -jar law-app-1.0-SNAPSHOT.jar --job=fixJob

# Via Maven
mvn spring-boot:run -pl law-app -Dspring-boot.run.arguments="--job=fixJob"

# Sans démarrer serveur web
java -jar law-app.jar --job=fixJob
```

### Exécution Quotidienne (Recommandé)

Ajouter au crontab pour maintenance automatique :

```bash
# Tous les jours à 2h du matin
0 2 * * * cd /path/to/io.law && java -jar law-app/target/law-app-1.0-SNAPSHOT.jar --job=fixJob >> logs/fix-cron.log 2>&1
```

### Intégration Pipeline

Script d'orchestration avec correction automatique entre chaque job :

```bash
#!/bin/bash
# orchestrate-with-fix.sh

echo "📥 1. Fetch métadonnées..."
java -jar law-app.jar --job=fetchCurrentJob

echo "🔧 Correction après fetch..."
java -jar law-app.jar --job=fixJob

echo "📥 2. Download PDFs..."
java -jar law-app.jar --job=downloadJob

echo "🔧 Correction après download..."
java -jar law-app.jar --job=fixJob

echo "📄 3. Extraction JSON..."
java -jar law-app.jar --job=pdfToJsonJob

echo "🔧 Correction après extraction..."
java -jar law-app.jar --job=fixJob

echo "💾 4. Consolidation BD..."
java -jar law-app.jar --job=consolidateJob

echo "🔧 Correction finale..."
java -jar law-app.jar --job=fixJob

echo "✅ Pipeline complet terminé avec corrections"
```

---

## 📈 Métriques d'Amélioration

### KPIs à Suivre

Après chaque exécution de `fixJob`, analyser :

1. **Taux auto-correction** : `corrigés / détectés`
   - Objectif : > 80%
   - Exemple : 189/237 = 79.7%

2. **Documents bloqués par statut** :
   - PENDING : Combien ne progressent pas ?
   - FETCHED : Problèmes download ?
   - DOWNLOADED : Problèmes extraction ?
   - EXTRACTED : Problèmes consolidation ?

3. **Qualité moyenne** :
   - Confiance moyenne avant/après corrections
   - Taux séquence OK avant/après
   - Taux mots reconnus avant/après

4. **Taux re-traitement** :
   - Documents nécessitant > 1 correction
   - Documents corrigés mais re-bloqués

### Requêtes SQL Utiles

```sql
-- Documents nécessitant correction (détectés par fixJob)
SELECT status, COUNT(*) as nb
FROM law_documents
WHERE status IN ('PENDING', 'FETCHED', 'DOWNLOADED', 'EXTRACTED')
GROUP BY status;

-- Qualité extraction actuelle
SELECT 
    AVG(JSON_EXTRACT(content, '$._metadata.confidence')) as avg_confidence,
    AVG(JSON_EXTRACT(content, '$._metadata.sequenceScore')) as avg_sequence,
    AVG(JSON_EXTRACT(content, '$._metadata.unrecognizedWordsRate')) as avg_unrecognized
FROM consolidated_metadata;

-- Documents à faible confiance (<0.3)
SELECT documentId, 
       JSON_EXTRACT(content, '$._metadata.confidence') as confidence
FROM consolidated_metadata
WHERE JSON_EXTRACT(content, '$._metadata.confidence') < 0.3;
```

---

## 🧪 Tests

### Test Unitaire

```bash
mvn test -pl law-fix
```

### Test Intégration (Base Réelle)

```bash
# 1. Backup base avant test
docker exec mysql-law mysqldump -u root -proot law_db > backup-before-fix.sql

# 2. Lancer fixJob
java -jar law-app.jar --job=fixJob

# 3. Vérifier résultats
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "SELECT status, COUNT(*) FROM law_documents GROUP BY status;"

# 4. Restaurer si nécessaire
docker exec -i mysql-law mysql -u root -proot law_db < backup-before-fix.sql
```

---

## 🔍 Dépannage

### Job Ne Corrige Rien

**Symptôme** : `fixJob` s'exécute mais 0 corrections appliquées.

**Diagnostic** :
```bash
# Vérifier documents non-finaux
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "SELECT status, COUNT(*) FROM law_documents WHERE status != 'CONSOLIDATED' GROUP BY status;"
```

**Solution** :
- Si tous documents = CONSOLIDATED : Aucune correction nécessaire ✅
- Si documents bloqués : Vérifier logs pour comprendre pourquoi non détectés

### Corrections Répétées Sans Succès

**Symptôme** : Même document corrigé à chaque exécution mais reste bloqué.

**Diagnostic** :
```bash
# Vérifier statut + fichiers
echo "Document: loi-2024-15"
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "SELECT status FROM law_documents WHERE document_id = 'loi-2024-15';"

ls -lh data/pdfs/loi/loi-2024-15.pdf
ls -lh data/ocr/loi/loi-2024-15.txt
ls -lh data/articles/loi/loi-2024-15.json
```

**Solution** :
- MISSING_PDF récurrent → Vérifier downloadJob fonctionne
- LOW_CONFIDENCE récurrent → Améliorer corrections.csv + patterns OCR
- SEQUENCE_ISSUES récurrent → Document vraiment incomplet sur SGG

### Performance Dégradée

**Symptôme** : `fixJob` prend > 30 minutes.

**Diagnostic** :
```bash
# Compter documents
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "SELECT COUNT(*) as total FROM law_documents;"
```

**Solution** :
- > 10 000 documents : Augmenter chunk-size dans `FixJobConfiguration`
- Problèmes I/O : Vérifier disque (trop de fichiers JSON à parser)
- Optimiser : Ajouter index DB sur `status` si nécessaire

---

## 🎯 Bénéfices

1. **Maintenance Proactive** : Détecte problèmes avant qu'ils bloquent pipeline
2. **Auto-guérison** : 80%+ problèmes corrigés automatiquement
3. **Amélioration Continue** : Qualité augmente à chaque exécution
4. **Non-bloquant** : Pipeline continue même si corrections échouent
5. **Traçabilité** : Logs détaillés pour chaque correction

---

## 📚 Références

- **[fix.md](../modules/fix.md)** : Documentation module law-fix
- **[architecture.md](../guides/architecture.md)** : Architecture globale
- **[functional.md](../guides/functional.md)** : Guide fonctionnel
- **[sequence-quality.md](sequence-quality.md)** : Pénalité séquence articles

---

**Date création** : 10 décembre 2025  
**Version** : 1.0-SNAPSHOT  
**Job ID** : `fixJob`
