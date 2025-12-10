# law-fix

Module de correction automatique et amélioration continue de la qualité des données.

## 🎯 Objectif

Détecter et corriger automatiquement les problèmes dans le pipeline de traitement :
- Documents bloqués dans un statut
- Fichiers manquants ou corrompus  
- Problèmes de qualité d'extraction (confiance faible, séquence articles, mots non reconnus)
- Données incohérentes en base de données

## 🏗️ Architecture

### Détecteurs (Detectors)

```
StatusIssueDetector     → Détecte documents bloqués (PENDING, FETCHED, DOWNLOADED, EXTRACTED)
FileIssueDetector       → Détecte fichiers manquants (PDF, OCR, JSON) ou corrompus
QualityIssueDetector    → Détecte problèmes qualité (confiance, séquence, mots non reconnus)
```

### Services de Correction (Fix Services)

```
StatusFixService        → Réinitialise statut pour déblocage
FileFixService          → Supprime fichiers corrompus, réinitialise statut
QualityFixService       → Force re-extraction pour améliorer qualité
```

### Orchestration

```
FixOrchestrator         → Coordonne détection + correction pour chaque document
FixProcessor (Batch)    → Traite documents par chunks
AllDocumentsReader      → Lit TOUS les documents (tous statuts)
```

## 🔄 Fonctionnement

### Pipeline du Job `fixJob`

```
1. AllDocumentsReader → Charge tous les documents depuis law_documents
2. FixProcessor → Pour chaque document:
   a. Détecte tous les problèmes (statut, fichiers, qualité)
   b. Priorise par sévérité (CRITICAL > HIGH > MEDIUM > LOW)
   c. Applique corrections automatiques si possible
   d. Log résultats (succès/échec/ignoré)
3. FixWriter → Finalise le batch (corrections déjà appliquées)
```

### Principe d'Amélioration Continue

Le job `fixJob` ne **bloque jamais** le pipeline. Stratégie :

1. **Détection non-intrusive** : Analyse tous les documents sans interrompre
2. **Auto-correction sélective** : Corrige uniquement ce qui est auto-fixable
3. **Réinitialisation intelligente** : Remet documents à bon statut pour re-traitement
4. **Logs détaillés** : Signale problèmes non auto-fixables pour intervention manuelle

## 📊 Types de Problèmes Détectés

### Problèmes de Statut (Auto-fixables ✅)

| Type | Description | Correction |
|------|-------------|------------|
| `STUCK_IN_PENDING` | Document en PENDING trop longtemps | Aucune (fetch à relancer) |
| `STUCK_IN_FETCHED` | PDF non téléchargé | Aucune (download à relancer) |
| `STUCK_IN_DOWNLOADED` | Extraction non effectuée | Aucune (extract à relancer) |
| `STUCK_IN_EXTRACTED` | Consolidation non effectuée | Aucune (consolidate à relancer) |

### Problèmes de Fichiers

| Type | Description | Correction |
|------|-------------|------------|
| `MISSING_PDF` ✅ | PDF manquant alors que status=DOWNLOADED+ | Réinitialise → FETCHED |
| `MISSING_OCR` ✅ | OCR manquant alors que status=EXTRACTED+ | Réinitialise → DOWNLOADED |
| `MISSING_JSON` ✅ | JSON manquant alors que status=EXTRACTED+ | Réinitialise → DOWNLOADED |
| `CORRUPTED_PDF` ❌ | PDF corrompu détecté | Supprime PDF, réinitialise → FETCHED |

### Problèmes de Qualité

| Type | Description | Seuil | Correction |
|------|-------------|-------|------------|
| `LOW_CONFIDENCE` ✅ | Confiance extraction < 30% | 0.3 | Réinitialise → DOWNLOADED |
| `SEQUENCE_ISSUES` ✅ | Gaps/duplicates/inversions articles | > 0 | Réinitialise → DOWNLOADED |
| `HIGH_UNRECOGNIZED_WORDS` ✅ | Taux mots non reconnus > 50% | 0.5 | Réinitialise → DOWNLOADED |
| `MISSING_ARTICLES` ✅ | Aucun article extrait | 0 | Réinitialise → DOWNLOADED |

## 🚀 Usage

### Exécution Manuelle

```bash
# Lancer le job de correction
java -jar law-app.jar --job=fixJob

# Avec profil Spring
mvn spring-boot:run -pl law-app -Dspring-boot.run.arguments="--job=fixJob"
```

### Exécution Quotidienne (Recommandé)

Ajouter au cron pour exécution automatique :

```bash
# Tous les jours à 2h du matin
0 2 * * * cd /path/to/io.law && java -jar law-app.jar --job=fixJob >> logs/fix-cron.log 2>&1
```

### Après Chaque Batch de Jobs

```bash
#!/bin/bash
# Script d'orchestration avec correction automatique

# 1. Lancer les jobs principaux
java -jar law-app.jar --job=fetchCurrentJob
java -jar law-app.jar --job=downloadJob
java -jar law-app.jar --job=pdfToJsonJob
java -jar law-app.jar --job=consolidateJob

# 2. Détecter et corriger problèmes
java -jar law-app.jar --job=fixJob

# 3. Re-lancer jobs pour documents corrigés
java -jar law-app.jar --job=downloadJob
java -jar law-app.jar --job=pdfToJsonJob
java -jar law-app.jar --job=consolidateJob
```

## 📋 Logs

### Format des Logs

```
🔍 [loi-2024-15] Analyse document (status=DOWNLOADED)
📋 [loi-2024-15] 2 problème(s) détecté(s)
🔧 [loi-2024-15] Tentative correction: MISSING_OCR
✅ [loi-2024-15] Corrigé: MISSING_OCR - Réinitialisé à DOWNLOADED
📊 [loi-2024-15] Corrections: 1 succès, 0 échecs, 1 ignorés
```

### Statistiques Globales

Exemple de sortie du job :

```
📄 1234 documents à analyser
   PENDING : 50 documents
   FETCHED : 120 documents
   DOWNLOADED : 300 documents
   EXTRACTED : 450 documents
   CONSOLIDATED : 314 documents
   
🔧 237 problèmes détectés
   ✅ 189 corrigés automatiquement
   ❌ 12 échecs correction
   ⏭️  36 ignorés (non auto-fixables)
```

## 🔧 Configuration

Pas de configuration spécifique nécessaire. Le module utilise :
- `FileStorageService` (depuis law-common)
- `LawDocumentRepository` (depuis law-common)
- Configuration Spring Batch héritée

## 🎯 Stratégie Correction

### Principes

1. **Non-bloquant** : Jamais d'exception qui arrête le job
2. **Idempotent** : Re-lancer fixJob N fois = même résultat
3. **Sélectif** : Corrige uniquement ce qui est auto-fixable
4. **Transparent** : Logs détaillés de chaque action

### Priorisation

Issues triées par sévérité avant correction :

```
CRITICAL (fichiers manquants bloquants)
  ↓
HIGH (problèmes qualité majeurs)
  ↓
MEDIUM (optimisations possibles)
  ↓
LOW (améliorations mineures)
```

### Limitations

**Non auto-fixables** (intervention manuelle requise) :
- URL SGG retourne 404 (document inexistant)
- Timeout download récurrent (problème réseau)
- Données incohérentes complexes

## 📊 Métriques

À suivre pour amélioration continue :

- **Taux auto-correction** : % problèmes corrigés automatiquement
- **Documents bloqués** : Nombre par statut
- **Qualité moyenne** : Confiance moyenne après corrections
- **Taux re-traitement** : Documents nécessitant plusieurs passes

## 🧪 Tests

```bash
# Tests unitaires
mvn test -pl law-fix

# Test complet avec base réelle
mvn spring-boot:run -pl law-app -Dspring-boot.run.arguments="--job=fixJob --spring.profiles.active=dev"
```

---

**Date création** : 10 décembre 2025  
**Version** : 1.0-SNAPSHOT  
**Objectif** : Amélioration continue sans blocage pipeline
