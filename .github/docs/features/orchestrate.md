# Orchestration Continue - orchestrate

## Description

Le mode `orchestrate` exécute de manière **continue et cyclique** l'ensemble du pipeline de traitement jusqu'à arrêt manuel (Ctrl+C).

## 🔄 Pipeline Exécuté

Chaque cycle exécute séquentiellement :

```
1. fetchCurrentJob      → Détection nouveaux documents année courante
2. downloadJob          → Téléchargement PDFs
3. pdfToJsonJob         → Extraction OCR/IA
4. consolidateJob       → Import en base de données
5. fixJob               → Correction automatique & amélioration continue
```

## 🎯 Objectif

**Automatisation complète** :
- Détection automatique de nouveaux documents sur SGG
- Traitement complet sans intervention
- Correction automatique des erreurs
- Amélioration continue de la qualité
- **Exécution infinie** jusqu'à arrêt manuel

## 🚀 Usage

### Démarrage

```bash
# Démarrer l'orchestration continue
java -jar law-app-1.0-SNAPSHOT.jar --job=orchestrate
```

### Arrêt

```bash
# Ctrl+C dans le terminal
^C
```

Le signal SIGINT (Ctrl+C) est capturé proprement :
- Arrêt du cycle en cours après le job actuel
- Logs finaux avec nombre de cycles exécutés
- Sortie propre de l'application

## ⚙️ Configuration

### Délai Entre Cycles

Par défaut : **60 secondes** (1 minute)

Modifiable dans `PipelineOrchestrator.java` :

```java
private static final long CYCLE_DELAY_MS = 60_000; // 1 minute
```

Exemples :
- `30_000` : 30 secondes (test rapide)
- `300_000` : 5 minutes (production)
- `900_000` : 15 minutes (faible fréquence)

### Gestion des Erreurs

**Comportement** :
- Échec d'un job → Log erreur + continue au job suivant
- Échec critique → Log warning + cycle suivant réessaie
- **Jamais d'arrêt automatique** sur erreur

**Exceptions non-bloquantes** :
- `JobExecutionAlreadyRunningException` : Skip, continue
- `JobRestartException` : Log, continue
- `JobInstanceAlreadyCompleteException` : Log, continue

## 📊 Logs

### Démarrage

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🚀 DÉMARRAGE ORCHESTRATION CONTINUE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📋 Pipeline: fetch → download → extract → consolidate → fix
🔄 Mode: Continu (arrêt: Ctrl+C)
⏱️  Délai entre cycles: 60000ms
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### Cycle

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🔄 CYCLE #1 - 2025-12-10 10:30:00
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

▶️  1/5 📡 Fetch métadonnées - fetchCurrentJob
─────────────────────────────────────────────────────────────
✅ fetchCurrentJob terminé: COMPLETED

▶️  2/5 📥 Download PDFs - downloadJob
─────────────────────────────────────────────────────────────
✅ downloadJob terminé: COMPLETED

▶️  3/5 📄 Extraction JSON - pdfToJsonJob
─────────────────────────────────────────────────────────────
✅ pdfToJsonJob terminé: COMPLETED

▶️  4/5 💾 Consolidation BD - consolidateJob
─────────────────────────────────────────────────────────────
✅ consolidateJob terminé: COMPLETED

▶️  5/5 🔧 Correction & amélioration - fixJob
─────────────────────────────────────────────────────────────
✅ fixJob terminé: COMPLETED

✅ Cycle #1 terminé avec succès
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

⏸️  Pause 60 secondes avant prochain cycle...
```

### Arrêt

```
^C
⏹️  Signal d'arrêt reçu (Ctrl+C)
⏹️  Arrêt de l'orchestration demandé
🏁 Orchestration terminée - 12 cycles exécutés
👋 Arrêt de l'application
```

## 🎯 Cas d'Usage

### 1. Production - Monitoring Continu

```bash
# Serveur dédié avec logs
nohup java -jar law-app.jar --job=orchestrate > logs/orchestrator.log 2>&1 &

# Suivre les logs
tail -f logs/orchestrator.log
```

### 2. Développement - Test Pipeline

```bash
# Cycle rapide pour développement
# Modifier CYCLE_DELAY_MS = 10_000 (10s)
java -jar law-app.jar --job=orchestrate
```

### 3. Cron Quotidien - Batch Limité

Si l'orchestration continue n'est pas souhaitée, préférer :

```bash
# Crontab : Exécution quotidienne à 2h
0 2 * * * cd /path/to/io.law && ./scripts/run-pipeline.sh
```

**run-pipeline.sh** :
```bash
#!/bin/bash
java -jar law-app.jar --job=fetchCurrentJob
java -jar law-app.jar --job=downloadJob
java -jar law-app.jar --job=pdfToJsonJob
java -jar law-app.jar --job=consolidateJob
java -jar law-app.jar --job=fixJob
```

## 📈 Monitoring

### Vérifier État Pipeline

```bash
# Compter documents par statut
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "SELECT status, COUNT(*) as nb FROM law_documents GROUP BY status;"
```

### Vérifier Progression

```bash
# Documents traités aujourd'hui
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "SELECT 
    DATE(updated_at) as date,
    status,
    COUNT(*) as nb
  FROM law_documents
  WHERE DATE(updated_at) = CURDATE()
  GROUP BY DATE(updated_at), status;"
```

### Logs Orchestrateur

```bash
# Filtrer logs orchestration
grep "CYCLE #" logs/orchestrator.log

# Compter cycles exécutés
grep "Cycle #.*terminé avec succès" logs/orchestrator.log | wc -l

# Voir erreurs
grep "❌" logs/orchestrator.log
```

## 🔧 Personnalisation

### Modifier Ordre des Jobs

Dans `PipelineOrchestrator.executeCycle()` :

```java
// Exemple : Ajouter fetchPreviousJob avant les autres
executeJob(fetchPreviousJob, "0/5 📡 Fetch années précédentes");
executeJob(fetchCurrentJob, "1/5 📡 Fetch année courante");
// ...
```

### Ajouter Condition d'Arrêt

```java
// Arrêt après N cycles
if (cycleCount.get() >= 100) {
    log.info("🎯 100 cycles atteints, arrêt automatique");
    stopOrchestration();
}

// Arrêt si aucun nouveau document
if (noNewDocumentsCount >= 5) {
    log.info("💤 Aucun nouveau document depuis 5 cycles, arrêt");
    stopOrchestration();
}
```

### Alertes Email/Slack

```java
// Après chaque cycle
if (!success) {
    notificationService.sendAlert(
        "⚠️ Cycle #" + cycle + " échoué",
        "Voir logs pour détails"
    );
}
```

## ⚠️ Limitations

1. **Pas de parallélisation** : Jobs exécutés séquentiellement
2. **Pas de reprise automatique** : Arrêt → redémarrage manuel
3. **Pas de priorité** : Tous documents traités dans l'ordre
4. **Pas de throttling** : Charge constante sur SGG et BD

## 🎯 Améliorations Futures

- [ ] **Paramètres CLI** : `--delay=300000`, `--max-cycles=10`
- [ ] **Mode parallèle** : Exécuter plusieurs jobs simultanément
- [ ] **Health check** : API REST pour vérifier état orchestrateur
- [ ] **Métriques** : Prometheus/Grafana pour monitoring
- [ ] **Retry automatique** : Re-tenter jobs échoués avec backoff
- [ ] **Throttling adaptatif** : Ajuster délai selon charge SGG

## 📚 Références

- **[architecture.md](../guides/architecture.md)** : Vue d'ensemble pipeline
- **[functional.md](../guides/functional.md)** : Description jobs individuels
- **[fixjob.md](fixjob.md)** : Détails job de correction
- **[fulljob.md](fulljob.md)** : Pipeline pour document unique

---

**Date création** : 10 décembre 2025  
**Version** : 1.0-SNAPSHOT  
**Mode** : Orchestration continue (Ctrl+C pour arrêter)
