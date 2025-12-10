# Documentation io.law

Index complet de la documentation du projet.

## 📚 Point d'Entrée

- **[copilot-instructions.md](../copilot-instructions.md)** : Instructions GitHub Copilot avec résumé essentiel
- **[README.md](../../README.md)** : Vue d'ensemble du projet

---

## 📖 Guides Principaux

Documentation fondamentale pour comprendre et développer le projet.

| Guide | Description | Lignes |
|-------|-------------|--------|
| **[architecture.md](guides/architecture.md)** | Structure multi-modules, flux de données, état du projet | 213 |
| **[technical.md](guides/technical.md)** | Clean code, patterns, OCR, qualité extraction, build & test | 419 |
| **[functional.md](guides/functional.md)** | Configuration, jobs, pipeline, API REST, SQL | 494 |

---

## ⚡ Features & Jobs

Documentation des fonctionnalités avancées et pipelines.

| Feature | Description | Lignes |
|---------|-------------|--------|
| **[fulljob.md](features/fulljob.md)** | Pipeline complet automatique (fetch → download → extract → consolidate) | 352 |
| **[sequence-quality.md](features/sequence-quality.md)** | Pénalité confiance basée sur séquence d'articles | 257 |
| **[fixjob.md](features/fixjob.md)** | Correction automatique et amélioration continue (fixJob) | 520 |

---

## 🔧 Modules

Documentation spécifique par module.

| Module | Description | Lignes |
|--------|-------------|--------|
| **[consolidate.md](modules/consolidate.md)** | law-consolidate : Import JSON → MySQL, entités JPA, repositories | 279 |
| **[json-config.md](modules/json-config.md)** | law-json-config : Job pdfToJsonJob, stratégie fallback IA/OCR | 177 |
| **[fix.md](modules/fix.md)** | law-fix : Correction automatique et amélioration continue qualité données | 287 |

---

## 🧪 Ressources de Tests

> **Note** : Documentations conservées localement dans les modules car elles référencent des ressources de test spécifiques.

| Emplacement | Description |
|-------------|-------------|
| `law-ai-pdf-json/src/test/java/README.md` | Tests extraction IA (29 tests) |
| `law-ocr-json/src/test/resources/samples_ocr/INDEX.md` | Index échantillons OCR (47 fichiers) |
| `law-ocr-json/src/test/resources/samples_json/README.md` | Échantillons JSON extraits (38 fichiers) |
| `law-pdf-ocr/src/main/resources/tessdata/README.md` | Instructions installation Tesseract |

---

## 🗂️ Structure

```
.github/docs/
├── INDEX.md                    # Ce fichier - Point d'entrée
│
├── guides/                     # 📖 Documentation fondamentale
│   ├── architecture.md         # Structure & flux
│   ├── technical.md            # Pratiques de développement
│   └── functional.md           # Usage & configuration
│
├── features/                   # ⚡ Fonctionnalités avancées
│   ├── fulljob.md              # Pipeline automatique
│   └── sequence-quality.md     # Qualité extraction
│
└── modules/                    # 🔧 Documentation modules
    ├── consolidate.md          # law-consolidate
    └── json-config.md          # law-json-config
```

---

## 📖 Parcours de Lecture

### 🚀 Démarrage Rapide
1. [README.md](../../README.md) - Vue d'ensemble du projet
2. [copilot-instructions.md](../copilot-instructions.md) - Résumé essentiel & conventions
3. [guides/architecture.md](guides/architecture.md) - Comprendre la structure

### 👨‍💻 Développement
4. [guides/technical.md](guides/technical.md) - Principes clean code & patterns
5. [guides/functional.md](guides/functional.md) - Jobs & configuration
6. [modules/*.md](modules/) - Documentation spécifique par module

### ⚡ Features Avancées
7. [features/fulljob.md](features/fulljob.md) - Pipeline complet automatique
8. [features/sequence-quality.md](features/sequence-quality.md) - Système de qualité OCR

---

**Dernière mise à jour** : 9 décembre 2025
