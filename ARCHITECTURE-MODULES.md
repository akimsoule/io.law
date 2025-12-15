# Architecture Multi-Modules Sans Spring

## Principes de Séparation

Chaque module a une **responsabilité unique** et peut être compilé/testé **indépendamment**.

### Arborescence

```
io.law/
├── pom-nospring.xml                 # Parent multi-modules (gère versions)
│
├── law-common/                      # 🔧 SOCLE PARTAGÉ
│   ├── pom-nospring.xml             # Gson + SLF4J + Logback + Commons-IO
│   └── src/main/java/.../
│       ├── model/                   # DocumentRecord, ProcessingStatus
│       ├── storage/                 # JsonStorage<T> (persistence files)
│       ├── config/                  # AppConfig (properties loader)
│       ├── service/                 # DocumentService, FileStorageService
│       ├── exception/               # Exceptions métier (21 types)
│       └── util/                    # DateUtils, StringUtils, ValidationUtils
│
├── law-fetch/                       # 📡 MODULE FETCH
│   ├── pom-nospring.xml             # Dépend de: law-common
│   └── src/main/java/.../
│       └── job/FetchJob.java        # HTTP HEAD checks (current + previous years)
│
├── law-download/                    # ⬇️ MODULE DOWNLOAD
│   ├── pom-nospring.xml             # Dépend de: law-common
│   └── src/main/java/.../
│       └── job/DownloadJob.java     # HTTP GET + SHA-256 (idempotent)
│
├── law-tojson/                      # 📄 MODULE EXTRACTION (parent)
│   ├── pom-nospring.xml             # Parent des sous-modules
│   │
│   ├── law-pdf-ocr/                 # OCR extraction
│   │   ├── pom-nospring.xml         # Dépend de: law-common + PDFBox + Tesseract
│   │   └── src/main/java/.../
│   │       └── job/OcrJob.java      # PDF → TXT (detect corruption)
│   │
│   ├── law-ocr-json/                # Parsing extraction
│   │   ├── pom-nospring.xml         # Dépend de: law-common
│   │   └── src/main/java/.../
│   │       └── job/ExtractJob.java  # TXT → JSON (regex + confidence)
│   │
│   └── law-ai/                      # IA extraction (optionnel)
│       ├── pom-nospring.xml         # Dépend de: law-common + OkHttp
│       └── src/main/java/.../
│           └── job/AIJob.java       # PDF → JSON via Ollama/Groq
│
├── law-consolidate/                 # 🗂️ MODULE CONSOLIDATION
│   ├── pom-nospring.xml             # Dépend de: law-common
│   └── src/main/java/.../
│       └── job/ConsolidateJob.java  # JSON → aggregation/MySQL
│
├── law-fix/                         # 🔧 MODULE FIX
│   ├── pom-nospring.xml             # Dépend de: law-common
│   └── src/main/java/.../
│       └── job/FixJob.java          # Detect missing files, reset statuses
│
└── law-app/                         # 🚀 CLI ORCHESTRATEUR
    ├── pom-nospring.xml             # Dépend de: TOUS les modules
    │                                # Maven Shade Plugin → JAR exécutable
    └── src/main/java/.../
        └── cli/LawCli.java          # Entry point + routing jobs
```

## Graphe de Dépendances

```
law-common (socle)
    ↑
    ├─── law-fetch
    ├─── law-download
    ├─── law-tojson
    │       ├─── law-pdf-ocr
    │       ├─── law-ocr-json
    │       └─── law-ai
    ├─── law-consolidate
    └─── law-fix

law-app (dépend de TOUS)
```

## Responsabilités Modules

### law-common (Socle Partagé)
**Aucune logique métier job**, uniquement:
- **model/** : Entités POJO (sans JPA)
- **storage/** : `JsonStorage<T>` pour persistence fichiers JSON
- **config/** : `AppConfig` singleton pour properties
- **service/** : Services réutilisables (`DocumentService`, `FileStorageService`)
- **exception/** : 21 exceptions métier spécifiques
- **util/** : Utilitaires statiques

**Zéro dépendance** vers les autres modules.

### law-fetch (Récupération Métadonnées)
- `FetchJob.java` : HTTP HEAD pour vérifier existence documents
- **Modes** : `runCurrent(type)` année courante, `runPrevious(type, maxItems)` 1960→année-1
- **Parallélisation** : ExecutorService configurable
- **Stockage** : JSON (`fetch_results.json`, `fetch_cursors.json`)

### law-download (Téléchargement PDFs)
- `DownloadJob.java` : HTTP GET pour télécharger PDFs
- **Idempotence** : Skip si fichier existe avec même SHA-256
- **Parallélisation** : ExecutorService configurable
- **Stockage** : Fichiers PDF + JSON (`download_results.json`)

### law-tojson (Extraction Contenu)
#### law-pdf-ocr
- `OcrJob.java` : PDFBox + Tesseract → fichiers `.txt`
- Détection corruption (PNG déguisé, magic bytes)
- Idempotence: skip si `.txt` existe

#### law-ocr-json
- `ExtractJob.java` : Parsing regex `.txt` → `.json`
- Extraction articles, signataires, métadonnées
- Anti-écrasement si confiance inférieure

#### law-ai (optionnel)
- `AIJob.java` : Extraction via Ollama local ou Groq API
- Fallback automatique si IA indisponible
- Stratégie de priorisation (IA > OCR)

### law-consolidate (Consolidation)
- `ConsolidateJob.java` : Lecture JSON + agrégation
- Options: fichiers consolidés OU MySQL JDBC minimal

### law-fix (Réparation)
- `FixJob.java` : Détection fichiers manquants
- Reset statuts incohérents
- Régénération si nécessaire

### law-app (CLI Orchestrateur)
- `LawCli.java` : Point d'entrée unique
- Routing vers jobs des modules
- Arguments CLI: `--job`, `--type`, `--maxDocuments`, `--maxItems`
- Orchestration séquentielle: fetch → download → ocr → extract → consolidate

## Compilation Multi-Modules

### Ordre de compilation
```bash
cd /path/to/io.law

# 1. Parent (définit versions)
mvn clean install -DskipTests -f pom-nospring.xml -N

# 2. law-common (socle)
cd law-common
mvn clean install -DskipTests -f pom-nospring.xml

# 3. Modules dépendant de common
cd ../law-fetch
mvn clean install -DskipTests -f pom-nospring.xml

cd ../law-download
mvn clean install -DskipTests -f pom-nospring.xml

cd ../law-tojson/law-pdf-ocr
mvn clean install -DskipTests -f pom-nospring.xml

cd ../../law-tojson/law-ocr-json
mvn clean install -DskipTests -f pom-nospring.xml

cd ../law-consolidate
mvn clean install -DskipTests -f pom-nospring.xml

cd ../law-fix
mvn clean install -DskipTests -f pom-nospring.xml

# 4. law-app (dépend de tous)
cd ../law-app
mvn clean package -DskipTests -f pom-nospring.xml
```

### Compilation automatisée
```bash
# Depuis racine, compiler tous les modules dans l'ordre
mvn clean install -DskipTests -f pom-nospring.xml
```

## Exécution

```bash
# JAR généré
java -jar law-app/target/law-app-1.0-SNAPSHOT.jar --job=fetch --type=loi

# Ou via script
bash scripts/orchestrate.sh --once --type=loi
```

## Avantages Architecture

### ✅ Séparation des responsabilités
Chaque module = 1 job = 1 responsabilité claire.

### ✅ Tests isolés
Tester un module sans compiler les autres:
```bash
cd law-fetch
mvn test -f pom-nospring.xml
```

### ✅ Parallélisation compilation
Maven peut compiler modules indépendants en parallèle:
```bash
mvn clean install -T 4 -f pom-nospring.xml  # 4 threads
```

### ✅ Déploiement flexible
- **Option 1** : JAR unique (law-app avec tous modules shadés)
- **Option 2** : Modules séparés (classpath modulaire)
- **Option 3** : Conteneurs Docker par module

### ✅ Évolutivité
Ajouter un nouveau module:
1. Créer `law-newmodule/`
2. Ajouter `pom-nospring.xml` dépendant de `law-common`
3. Référencer dans `law-app` dependencies
4. Utiliser dans `LawCli.java`

### ✅ Réutilisabilité
`law-common` peut être partagé avec d'autres projets Java.

## Anti-Patterns à Éviter

### ❌ Dépendances circulaires
```
law-fetch → law-download  ❌ INTERDIT
law-download → law-fetch  ❌ INTERDIT
```

**Règle** : Seuls les modules peuvent dépendre de `law-common`.  
Si besoin de partage entre modules → déplacer dans `law-common`.

### ❌ Code métier dans law-common
```java
// ❌ INTERDIT dans law-common
public class FetchJob { ... }

// ✅ CORRECT dans law-fetch
public class FetchJob { ... }
```

**Règle** : `law-common` = infrastructure uniquement (models, storage, config, utils).

### ❌ Duplication de code
Si 2 modules ont du code similaire → refactorer dans `law-common/util/`.

## Migration Depuis Version Spring

### Remplacement POMs
```bash
# Remplacer POMs module par module
for module in law-common law-fetch law-download law-app; do
    cd $module
    mv pom.xml pom-spring-backup.xml
    mv pom-nospring.xml pom.xml
    cd ..
done

# Remplacer POM racine
mv pom.xml pom-spring-backup.xml
mv pom-nospring.xml pom.xml
```

### Suppression dépendances Spring
Rechercher et supprimer:
```bash
grep -r "spring-boot-starter" */pom.xml
grep -r "@SpringBootApplication" */src/main/java
grep -r "@Component" */src/main/java
```

### Tests migration
```bash
# Compiler nouveau système
mvn clean install -DskipTests

# Tester fetch
java -jar law-app/target/law-app-*.jar --job=fetch --type=loi

# Tester download
java -jar law-app/target/law-app-*.jar --job=download --type=loi --maxDocuments=5
```

## Performances

### Spring Boot/Batch
- Startup: **5-10s**
- RAM: **512MB-1GB**
- JAR: **50-80MB**

### Java Pur (multi-modules)
- Startup: **<1s**
- RAM: **128-256MB**
- JAR: **5-10MB**

### Gain
- **10x plus rapide** au startup
- **4x moins de RAM**
- **8x moins d'espace disque**
