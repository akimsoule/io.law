# Migration Sans Spring - io.law

## Vue d'ensemble

Migration complète du projet **io.law** de Spring Boot/Batch vers **Java pur 17**.

### Objectifs atteints
- ✅ Retrait complet de Spring Boot, Spring Batch, Spring Data JPA
- ✅ CLI standalone avec `java -jar law-app.jar`
- ✅ Persistance JSON/CSV simple (pas de base de données)
- ✅ HttpClient natif Java 17 (HEAD/GET)
- ✅ Jobs parallélisés avec ExecutorService
- ✅ Configuration via `application.properties`
- ✅ Logs SLF4J + Logback
- ✅ Idempotence conservée

## Architecture

### Modules simplifiés

```
io.law/
├── law-common/          # Socle sans Spring
│   ├── model/           # POJOs (DocumentRecord, ProcessingStatus)
│   ├── service/         # DocumentService, FileStorageService
│   ├── storage/         # JsonStorage<T> générique
│   ├── job/            # FetchJob, DownloadJob
│   └── config/          # AppConfig (Properties loader)
│
└── law-app/             # CLI standalone
    └── cli/             # LawCli (main entry point)
```

### Stockage JSON

Fichiers dans `data/db/`:
- `documents.json` : Liste de tous les documents avec statuts
- `fetch_results.json` : Historique des fetchs
- `fetch_cursors.json` : Curseurs années précédentes
- `download_results.json` : Historique downloads

### Jobs implémentés

1. **FetchJob** (`--job=fetch` ou `--job=fetchCurrent`)
   - Scan année courante (1-2000) ou années précédentes
   - HttpClient HEAD requests
   - Parallélisation avec ExecutorService
   - Statut: PENDING → FETCHED

2. **DownloadJob** (`--job=download`)
   - Télécharge PDFs des documents FETCHED
   - HttpClient GET + SHA-256 hashing
   - Idempotence: skip si déjà téléchargé
   - Statut: FETCHED → DOWNLOADED

3. **OcrJob** (`--job=ocr`) — À implémenter
   - PDFBox + Tesseract via JavaCPP
   - Statut: DOWNLOADED → EXTRACTED

4. **ExtractJob** (`--job=extract`) — À implémenter
   - Parsing regex OCR → JSON
   - Anti-écrasement si confiance inférieure
   - Statut: EXTRACTED → (JSON créé)

5. **ConsolidateJob** (`--job=consolidate`) — À implémenter
   - Lecture JSON + consolidation
   - Statut: → CONSOLIDATED

6. **FixJob** (`--job=fix`) — À implémenter
   - Détection fichiers manquants
   - Reset statuts incohérents

## Utilisation

### Compilation

```bash
# Compiler common
cd law-common
mvn clean install -DskipTests -f pom-nospring.xml

# Compiler app
cd ../law-app
mvn clean package -DskipTests -f pom-nospring.xml
```

### Exécution

```bash
# Aide
java -jar law-app/target/law-app-1.0-SNAPSHOT.jar --help

# Fetch année courante (loi)
java -jar law-app/target/law-app-1.0-SNAPSHOT.jar --job=fetch --type=loi

# Fetch années précédentes (max 100 items)
java -jar law-app/target/law-app-1.0-SNAPSHOT.jar --job=fetchPrevious --type=loi --maxItems=100

# Download (max 10 docs)
java -jar law-app/target/law-app-1.0-SNAPSHOT.jar --job=download --type=loi --maxDocuments=10

# Orchestration complète
java -jar law-app/target/law-app-1.0-SNAPSHOT.jar --job=orchestrate --type=loi
```

### Script orchestrate.sh

```bash
# Mode continu (boucle infinie)
bash scripts/orchestrate.sh

# Mode unique (1 passage)
bash scripts/orchestrate.sh --once --type=loi --limit-download=10
```

## Configuration

Éditer `law-common/src/main/resources/application.properties`:

```properties
# URLs
law.base-url=https://sgg.gouv.bj/doc
law.user-agent=Mozilla/5.0 (compatible; LawBatchBot/1.0)

# Stockage
law.storage.base-path=data
law.storage.pdf-dir=pdfs
law.storage.ocr-dir=ocr
law.storage.json-dir=articles

# HTTP
law.http.timeout=30000
law.http.max-retries=3
law.http.retry-delay=2000

# Batch
law.batch.max-threads=10
law.batch.chunk-size=10
```

## Dépendances

### law-common
- Gson (JSON)
- SLF4J + Logback (logs)
- Commons IO (file utils)
- Lombok (annotations)

### law-app
- law-common
- Maven Shade Plugin (JAR exécutable)

**Taille JAR** : ~5 MB (vs ~50 MB avec Spring)

## Tests

```bash
# Tests unitaires
mvn test -f pom-nospring.xml

# Tests d'intégration
mvn verify -f pom-nospring.xml
```

## Avantages vs Spring

| Aspect | Spring Boot/Batch | Java Pur |
|--------|------------------|----------|
| **Startup** | 5-10s | <1s |
| **RAM** | 512MB-1GB | 128MB-256MB |
| **JAR Size** | 50-80MB | 5-10MB |
| **Complexité** | Élevée | Faible |
| **Dépendances** | ~50 | ~5 |
| **Debugging** | Difficile | Simple |

## Prochaines étapes

1. ✅ Fetch + Download implémentés
2. ⏳ Implémenter OcrJob (PDFBox + Tesseract)
3. ⏳ Implémenter ExtractJob (regex parsing)
4. ⏳ Implémenter ConsolidateJob (JSON merger)
5. ⏳ Implémenter FixJob (repair utility)
6. ⏳ Ajouter mode continu avec boucle infinie
7. ⏳ Ajouter métriques (compteurs, timing)
8. ⏳ Tests end-to-end complets

## Migration depuis main

Pour adopter cette version:

```bash
# Merge depuis branche migration
git checkout main
git merge migration/rearchitecture-tojson

# Remplacer les POMs
cd law-common && mv pom-nospring.xml pom.xml
cd ../law-app && mv pom-nospring.xml pom.xml

# Supprimer anciens modules Spring
git rm -r law-fetch law-download law-tojson law-consolidate law-fix

# Recompiler
mvn clean install -DskipTests
```

## Support

Pour questions ou problèmes, voir:
- Issues GitHub: https://github.com/akimsoule/io.law/issues
- Documentation: `.github/copilot-instructions.md`
