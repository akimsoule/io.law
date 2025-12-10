# fullJob - Pipeline Complet

## Description

Le job `fullJob` exécute automatiquement le pipeline complet de traitement pour un document spécifique, de la récupération des métadonnées jusqu'à la consolidation en base de données.

## Pipeline d'Exécution

Le job exécute séquentiellement les 4 étapes suivantes :

```
1. fetchCurrentJob   → Récupère les métadonnées du document depuis SGG
2. downloadJob       → Télécharge le fichier PDF
3. pdfToJsonJob      → Extrait le contenu (OCR/IA) et génère le JSON
4. consolidateJob    → Consolide les données en base MySQL
```

## Usage

### ✅ Syntaxe Correcte (OBLIGATOIRE)

```bash
java -jar law-app-1.0-SNAPSHOT.jar --job=fullJob --doc=loi-2024-15
```

Le paramètre `--doc` est **OBLIGATOIRE**. Le job ne peut pas fonctionner sans ce paramètre.

### ❌ Syntaxe Incorrecte (ÉCHOUE)

```bash
# ERREUR : Manque le paramètre --doc
java -jar law-app-1.0-SNAPSHOT.jar --job=fullJob
```

**Message d'erreur attendu :**
```
❌ Paramètre --doc manquant pour fullJob
❌ Usage: java -jar law-app.jar --job=fullJob --doc=loi-2024-15 [--force=true]
Exception: Paramètre --doc obligatoire pour fullJob
```

## Paramètres

| Paramètre | Obligatoire | Description | Exemple |
|-----------|-------------|-------------|---------|
| `--job` | ✅ Oui | Nom du job à exécuter | `--job=fullJob` |
| `--doc` | ✅ Oui | ID du document à traiter | `--doc=loi-2024-15` |
| `--force` | ❌ Non | Force le retraitement complet (défaut: false) | `--force` ou `--force=true` |

## Exemples

### Traitement d'une loi de 2024

```bash
java -jar law-app-1.0-SNAPSHOT.jar \
  --job=fullJob \
  --doc=loi-2024-15 \
  --spring.main.web-application-type=none
```

### Traitement d'un décret

```bash
java -jar law-app-1.0-SNAPSHOT.jar \
  --job=fullJob \
  --doc=decret-2024-1632 \
  --spring.main.web-application-type=none
```

### Traitement d'une loi de 2025

```bash
java -jar law-app-1.0-SNAPSHOT.jar \
  --job=fullJob \
  --doc=loi-2025-18 \
  --spring.main.web-application-type=none
```

### 🔄 Retraitement avec --force

Forcer le retraitement complet d'un document déjà consolidé :

```bash
java -jar law-app-1.0-SNAPSHOT.jar \
  --job=fullJob \
  --doc=loi-2024-15 \
  --force \
  --spring.main.web-application-type=none
```

**Note** : Le mode `--force` active le retraitement même si le document est déjà dans un état final (`CONSOLIDATED`). Tous les steps seront réexécutés.

## Logs Attendus

Lors de l'exécution, vous verrez les logs suivants pour chaque étape :

```
✅ Document cible validé: loi-2024-15

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📄 ÉTAPE 1/4 : Fetch métadonnées pour loi-2024-15
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
...
✅ fetchCurrentJob terminé pour loi-2024-15

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📥 ÉTAPE 2/4 : Download PDF pour loi-2024-15
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
...
✅ downloadJob terminé pour loi-2024-15

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📄 ÉTAPE 3/4 : Extraction JSON pour loi-2024-15
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
...
✅ pdfToJsonJob terminé pour loi-2024-15

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
💾 ÉTAPE 4/4 : Consolidation BD pour loi-2024-15
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
...
✅ consolidateJob terminé pour loi-2024-15
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🎉 PIPELINE COMPLET TERMINÉ pour loi-2024-15
📊 Statut final: CONSOLIDATED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## Gestion des Erreurs

### Erreur à l'Étape 1 (Fetch)

Si le document n'existe pas sur le site SGG :
- Le job s'arrête immédiatement
- Statut : `FAILED` ou `NOT_FOUND`
- Les étapes suivantes ne sont pas exécutées

### Erreur à l'Étape 2 (Download)

Si le PDF est corrompu ou inaccessible :
- Le job s'arrête
- Statut : `CORRUPTED` ou `FAILED`
- Les étapes suivantes ne sont pas exécutées

### Erreur à l'Étape 3 (Extraction)

Si l'extraction OCR/IA échoue :
- Le job s'arrête
- Statut : `FAILED`
- Consolidation non effectuée

### Erreur à l'Étape 4 (Consolidation)

Si la consolidation échoue :
- Le job s'arrête
- Statut : `FAILED`
- Les données partielles peuvent être en base

## Cas d'Usage

### 1. Traitement Initial d'un Nouveau Document

```bash
# Le document n'existe pas encore dans la base
java -jar law-app.jar --job=fullJob --doc=loi-2025-20
```

### 2. Re-traitement Complet

```bash
# Le document existe déjà, mais on veut le re-traiter entièrement
# Note : Pour forcer le re-traitement, utiliser les jobs individuels avec --force
java -jar law-app.jar --job=fetchCurrentJob --doc=loi-2025-20 --force
java -jar law-app.jar --job=downloadJob --doc=loi-2025-20 --force
java -jar law-app.jar --job=pdfToJsonJob --doc=loi-2025-20 --force
java -jar law-app.jar --job=consolidateJob
```

### 3. Traitement Batch de Plusieurs Documents

```bash
# Script pour traiter plusieurs documents
for doc in loi-2025-17 loi-2025-18 loi-2025-19; do
  echo "Traitement de $doc..."
  java -jar law-app.jar --job=fullJob --doc=$doc --spring.main.web-application-type=none
  if [ $? -eq 0 ]; then
    echo "✅ $doc traité avec succès"
  else
    echo "❌ Échec pour $doc"
  fi
done
```

## Comparaison avec Jobs Individuels

### fullJob vs Jobs Individuels

| Aspect | fullJob | Jobs Individuels |
|--------|---------|------------------|
| Nombre de commandes | 1 | 4 |
| Flexibilité | Moyenne | Élevée |
| Reprise sur erreur | Non (arrêt) | Oui (étape par étape) |
| Force mode | Non supporté | Supporté (--force) |
| Usage recommandé | Nouveau document | Re-traitement partiel |

### Quand Utiliser fullJob ?

✅ **Utiliser fullJob quand :**
- Nouveau document jamais traité
- Pipeline complet nécessaire
- Pas besoin de contrôle granulaire
- Traitement automatisé/scripté

❌ **Éviter fullJob quand :**
- Besoin de forcer une étape spécifique
- Reprise après erreur partielle
- Debug d'une étape précise
- Document déjà partiellement traité

## Configuration

### Fichier `FullJobConfiguration.java`

La configuration du job se trouve dans :
```
law-app/src/main/java/bj/gouv/sgg/config/FullJobConfiguration.java
```

### Beans Spring Batch

Le job est composé de 5 steps :
1. `validateDocumentParameterStep` : Validation paramètre --doc
2. `executeFetchStep` : Exécution fetchCurrentJob
3. `executeDownloadStep` : Exécution downloadJob
4. `executeExtractionStep` : Exécution pdfToJsonJob
5. `executeConsolidationStep` : Exécution consolidateJob

Chaque step utilise un `Tasklet` qui lance un sous-job via `JobLauncher`.

## Monitoring

### Vérifier l'État d'un Document

```bash
# Après exécution de fullJob
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "SELECT document_id, status FROM law_documents WHERE document_id = 'loi-2024-15';"
```

### Vérifier les Articles Consolidés

```bash
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "SELECT COUNT(*) FROM consolidated_articles WHERE documentId = 'loi-2024-15';"
```

### Vérifier les Logs

```bash
# Logs application
tail -f logs/law-app.log | grep "loi-2024-15"

# Logs Spring Batch
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "SELECT job_instance_id, job_execution_id, status, start_time, end_time \
   FROM BATCH_JOB_EXECUTION \
   WHERE job_execution_id IN (
     SELECT job_execution_id FROM BATCH_JOB_EXECUTION_PARAMS \
     WHERE KEY_NAME = 'doc' AND STRING_VAL = 'loi-2024-15'
   ) ORDER BY start_time DESC LIMIT 5;"
```

## Tests

Le script de tests fonctionnels inclut des tests pour `fullJob` :

```bash
./scripts/functionnal-test.sh
```

Tests inclus :
1. ✅ Exécution avec `--doc=loi-2024-15` (doit réussir)
2. ❌ Exécution sans `--doc` (doit échouer)

## Notes Techniques

### Transactions

- Chaque sous-job gère ses propres transactions
- Le `JobLauncher` ne doit pas être appelé dans un contexte transactionnel
- Les `Tasklet` utilisent `RepeatStatus.FINISHED` (pas de transaction parent)

### Idempotence

- Chaque sous-job est idempotent
- Re-lancer `fullJob` avec le même `--doc` :
  - Fetch : Met à jour si document modifié sur SGG
  - Download : Skip si déjà téléchargé (sauf --force)
  - Extraction : Skip si déjà extrait (sauf --force)
  - Consolidation : Compare confiance, update si supérieure

### Performance

- Exécution séquentielle (pas de parallélisation)
- Temps estimé : 30-60 secondes par document
- Dépend de :
  - Taille du PDF
  - Qualité OCR
  - Nombre d'articles
  - Charge réseau/DB

## Dépannage

### Job Ne Démarre Pas

```bash
# Vérifier que le JAR est bien construit
ls -lh law-app/target/law-app-1.0-SNAPSHOT.jar

# Vérifier la connexion MySQL
docker exec -it mysql-law mysql -u root -proot law_db -e "SELECT 1;"
```

### Job Bloqué

```bash
# Vérifier les jobs en cours
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "SELECT * FROM BATCH_JOB_EXECUTION WHERE STATUS = 'STARTED';"

# Arrêter manuellement
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "UPDATE BATCH_JOB_EXECUTION SET STATUS = 'FAILED' WHERE JOB_EXECUTION_ID = {id};"
```

### Document Reste en FAILED

```bash
# Reset manuel du statut
docker exec -it mysql-law mysql -u root -proot law_db -e \
  "UPDATE law_documents SET status = 'PENDING' WHERE document_id = 'loi-2024-15';"

# Relancer fullJob
java -jar law-app.jar --job=fullJob --doc=loi-2024-15
```

## Références

- [architecture.md](../.github/docs/architecture.md) - Architecture globale
- [functional.md](../.github/docs/functional.md) - Guide fonctionnel
- [technical.md](../.github/docs/technical.md) - Guide technique
- [JobCommandLineRunner.java](src/main/java/bj/gouv/sgg/cli/JobCommandLineRunner.java) - Runner CLI
