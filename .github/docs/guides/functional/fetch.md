# Module law-fetch - Documentation Fonctionnelle

## Description

Module de **récupération des métadonnées** des documents légaux (lois et décrets) depuis https://sgg.gouv.bj/doc.

**Fonctions principales** :
- Scanner le site SGG pour détecter les documents disponibles
- Vérifier l'existence via HTTP HEAD requests
- Sauvegarder les métadonnées en base MySQL
- Gérer les erreurs temporaires (429) et définitives (404)

---

## Workflow Général

```
┌─────────────────────────────────────────────────────────────┐
│                      JOB FETCH                              │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│ READER                                                      │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Génère liste documents (loi + décret)                   │ │
│ │ • Année courante: 1-2000                                │ │
│ │ • Années précédentes: depuis cursor                     │ │
│ │ • Skip: documents FETCHED + plages NOT_FOUND            │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│ PROCESSOR (Chunk: 10 documents)                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ HTTP HEAD Request                                       │ │
│ │                                                         │ │
│ │   ┌──────┐   ┌──────┐   ┌──────┐                        │ │
│ │   │ 200  │   │ 404  │   │ 429  │                        │ │
│ │   └───┬──┘   └───┬──┘   └───┬──┘                        │ │
│ │       │          │          │                           │ │
│ │       │          │          │ Retry 1/3 (sleep 2s)      │ │
│ │       │          │          │ Retry 2/3 (sleep 4s)      │ │
│ │       │          │          │ Retry 3/3 (sleep 8s)      │ │
│ │       │          │          │                           │ │
│ │       ▼          ▼          ▼                           │ │
│ │   FETCHED     FAILED    RATE_LIMITED                    │ │
│ │   (succès)   (définitif)  (temporaire)                  │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│ WRITER                                                      │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 1. Sauvegarde fetch_results (UPSERT)                    │ │
│ │ 2. Sauvegarde law_documents (UPSERT)                    │ │
│ │ 3. Consolidation plages NOT_FOUND (si activé)           │ │
│ │ 4. Sauvegarde cursor (position actuelle)                │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│ RÉSULTAT                                                    │
│                                                             │
│ • FETCHED → Prêt pour téléchargement (law-download)         │
│ • FAILED → Ne sera plus re-testé                            │
│ • RATE_LIMITED → Sera repris automatiquement au prochain run│
└─────────────────────────────────────────────────────────────┘
```

### Gestion Statuts et Reprise

```
Run N                          Run N+1
─────                          ───────
HTTP HEAD                      Re-test document ?
    │                               │
    ├─ 200 → FETCHED                ├─ FETCHED → Skip ✓
    │        (succès)               │
    ├─ 404 → FAILED                 ├─ FAILED → Skip ✓
    │        (définitif)            │
    └─ 429 → RATE_LIMITED           └─ RATE_LIMITED → Re-génère et re-traite ♻
             (temporaire)                             │
                                                      ▼
                                                   HTTP HEAD → 200 → FETCHED ✓
```

---

## Jobs Disponibles

### fetchCurrentJob
Scan de **l'année courante** (numéros 1 à 2000).

```bash
# Scan complet
java -jar law-app.jar --job=fetchCurrentJob

# Document ciblé
java -jar law-app.jar --job=fetchCurrentJob --documentId=loi-2024-17

# Re-fetch forcé
java -jar law-app.jar --job=fetchCurrentJob --force=true
```

**Comportement** :
- Teste tous les numéros 1-2000 pour loi + décret
- Skip documents déjà FETCHED (sauf RATE_LIMITED)
- 10 threads concurrents, chunks de 10 documents

### fetchPreviousJob
Scan des **années 1960 à année-1** avec curseur persistant.

```bash
# Scan historique (continue depuis cursor)
java -jar law-app.jar --job=fetchPreviousJob

# Année spécifique
java -jar law-app.jar --job=fetchPreviousJob --year=1990
```

**Comportement** :
- Traite 100 documents par run (configurable)
- Skip plages NOT_FOUND connues (optimisation)
- Timeout 55min → restart automatique depuis cursor
- Consolidation des plages 404 consécutives

---

## Statuts des Documents

| Statut | Signification | Repris au prochain run ? |
|--------|---------------|--------------------------|
| `PENDING` | Créé, pas encore traité | ✅ Oui |
| `FETCHED` | Trouvé (HTTP 200) | ❌ Non (succès définitif) |
| `FAILED` | Introuvable (HTTP 404) | ❌ Non (échec définitif) |
| `RATE_LIMITED` | Rate limit (HTTP 429) | ✅ Oui (repris automatiquement) |

**Point clé** : Les documents avec status `RATE_LIMITED` sont automatiquement re-générés et re-traités au prochain run.

---

## Gestion des Erreurs

### 404 (Document Inexistant)
```
HTTP HEAD → 404 → Status: FAILED
→ Ne sera plus re-testé (échec définitif)
```

### 429 (Rate Limit Atteint)
```
HTTP HEAD → 429 après 3 retries → Status: RATE_LIMITED
→ Sera automatiquement repris au prochain run
```

**Retry automatique** :
- 3 tentatives avec backoff exponentiel (2s, 4s, 8s)
- Cas spécial : Test URL paddée pour numéros <10 (ex: loi-2024-5 → loi-2024-05)

---

## Optimisations

### Curseurs (fetchPreviousJob)
- Position scan sauvegardée en DB (table `fetch_cursor`)
- Reprend automatiquement où il s'est arrêté
- Garantit scan complet même avec timeout

### Plages NOT_FOUND
- Détection plages 404 consécutives (ex: loi-1990-150 à loi-1990-200)
- Sauvegarde dans table `fetch_not_found_ranges`
- Skip automatique lors des prochains scans
- Réduit ~30% des requêtes HTTP (années anciennes)

---

## Requêtes SQL Utiles

```sql
-- Statistiques par statut
SELECT status, COUNT(*) as count 
FROM fetch_results 
GROUP BY status;

-- Documents RATE_LIMITED (à reprise)
SELECT document_id, fetch_date
FROM fetch_results
WHERE status = 'RATE_LIMITED'
ORDER BY document_year DESC;

-- Plages 404 détectées
SELECT document_type, document_year, start_number, end_number,
       (end_number - start_number + 1) as range_size
FROM fetch_not_found_ranges
ORDER BY document_year DESC;

-- Cursors actuels
SELECT cursor_type, document_type, current_year, current_number
FROM fetch_cursor;
```

---

## Configuration

```yaml
law:
  base-url: https://sgg.gouv.bj/doc
  batch:
    chunk-size: 10
    max-threads: 10
    max-items-to-fetch-previous: 100
    job-timeout-minutes: 55
  http:
    timeout: 30000        # 30s
    max-retries: 3
    retry-delay: 2000     # 2s
```

---

## Modes d'Exécution

| Mode | Commande | Usage |
|------|----------|-------|
| Standard | `--job=fetchCurrentJob` | Scan complet (skip FETCHED) |
| Ciblé | `--documentId=loi-2024-17` | Traite 1 seul document |
| Force | `--force=true` | Re-traite TOUT (ignore statuts) |

---

## Points d'Attention

1. **Rate Limiting** : Le serveur SGG limite les requêtes → Statut RATE_LIMITED + reprise auto
2. **Timeout** : fetchPreviousJob peut dépasser 55min → Restart automatique depuis cursor
3. **Idempotence** : Re-lancer N fois = même résultat (UPSERT en DB)
4. **Padding URLs** : Numéros <10 testés avec/sans padding (loi-2024-5 ET loi-2024-05)

---

**Version** : 1.0-SNAPSHOT  
**Dernière mise à jour** : 13 décembre 2025
