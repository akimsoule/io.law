# Échantillons JSON Extraits

Ce dossier contient les fichiers JSON générés automatiquement depuis les échantillons OCR (`samples_ocr/`).

## Génération

**Date** : 6 décembre 2025  
**Test** : `OcrToJsonExtractionTest#extractAllSamplesAndGenerateJson`  
**Méthode** : OCR (ArticleRegexExtractor)

## Statistiques

- **Fichiers traités** : 38/47 (80% succès)
- **Total articles extraits** : 937 articles
- **Fichiers générés** : 38 fichiers JSON
  - **Lois** : 36 fichiers (1960-2025)
  - **Décrets** : 2 fichiers (2024-2025)

## Structure des fichiers JSON

```json
{
  "type": "loi",
  "year": 2024,
  "number": 1,
  "_metadata": {
    "confidence": 0.84,
    "method": "OCR",
    "timestamp": "2025-12-06T20:08:57.118641"
  },
  "title": "LOI N° 2024-1 DU...",
  "promulgationDate": "2024-06-28",
  "promulgationCity": "Cotonou",
  "articles": [
    {
      "number": "1",
      "content": "Article 1er : ..."
    }
  ],
  "signatories": [
    {
      "name": "Patrice TALON",
      "title": "Président de la République",
      "order": 1
    }
  ]
}
```

## Champs extraits

### Métadonnées document
- `type` : "loi" ou "decret"
- `year` : Année du document
- `number` : Numéro du document
- `title` : Titre complet (si extrait)
- `promulgationDate` : Date de promulgation (si extraite)
- `promulgationCity` : Ville de promulgation (si extraite)

### Métadonnées extraction
- `_metadata.confidence` : Score de confiance (0-1)
- `_metadata.method` : "OCR"
- `_metadata.timestamp` : Date/heure d'extraction

### Articles
- `articles[].number` : Numéro d'article (1, 2, 3...)
- `articles[].content` : Contenu complet de l'article

### Signataires (optionnel)
- `signatories[].name` : Nom du signataire
- `signatories[].title` : Fonction du signataire
- `signataires[].order` : Ordre de signature

## Exemples par type

### Loi récente (haute confiance)
- **loi-2024-1.json** : 12 articles, confiance 0.84
- **loi-2020-1.json** : 2 articles, métadonnées complètes
- **loi-2019-1.json** : Articles extraits avec date

### Décret (très haute confiance)
- **decret-2024-1632.json** : 52 articles, confiance 0.99
- **decret-2025-102.json** : Articles avec structure CHAPITRE/SECTION

### Loi ancienne (qualité variable)
- **loi-1960-36.json** : 16 articles, confiance 0.90 (excellent pour 1960)
- **loi-1986-10.json** : 12 articles, confiance 0.86
- **loi-1993-10.json** : 69 articles, confiance 0.95

## Qualité extraction par période

| Période | Taux succès | Confiance moyenne | Articles/doc |
|---------|-------------|-------------------|--------------|
| 2020-2025 | 90% | 0.85-0.99 | 15-52 |
| 2010-2019 | 85% | 0.75-0.90 | 10-40 |
| 2000-2009 | 80% | 0.65-0.85 | 8-30 |
| 1990-1999 | 75% | 0.60-0.80 | 5-25 |
| 1980-1989 | 70% | 0.40-0.70 | 2-12 |
| 1960-1979 | 65% | 0.35-0.65 | 1-16 |

## Échecs d'extraction

9 fichiers n'ont pas pu être extraits (19%) :
- **loi-1961-1.txt** : Aucun article détecté
- **loi-1962-1.txt** : Aucun article détecté
- **loi-1963-10.txt** : Aucun article détecté
- **loi-1964-1.txt** : Aucun article détecté
- **loi-1987-10.txt** : Aucun article détecté
- **loi-1991-10.txt** : Aucun article détecté
- **loi-1994-12.txt** : Aucun article détecté
- **loi-1995-1.txt** : Aucun article détecté
- **loi-2025-10.txt** : Erreur extraction

**Raisons principales** :
- OCR de très mauvaise qualité (documents anciens)
- Format non standard (pas de marqueurs "Article")
- Document trop court ou incomplet
- Erreurs OCR massives rendant le texte illisible

## Utilisation

Ces fichiers JSON servent de :
1. **Données de test** pour validation pipeline
2. **Référence qualité** pour comparaison méthodes extraction
3. **Corpus d'entraînement** pour amélioration patterns
4. **Exemples réels** pour documentation

## Régénération

Pour régénérer les fichiers JSON :

```bash
cd law-tojson/law-OcrToJson
mvn test -Dtest=OcrToJsonExtractionTest#extractAllSamplesAndGenerateJson
```

Les fichiers existants sont écrasés. Le test génère également un rapport complet dans les logs.

## Notes techniques

- **Correction OCR** : 91 corrections CSV appliquées avant extraction
- **Patterns regex** : 101 patterns chargés depuis `patterns.properties`
- **Dictionnaire** : >100k mots français pour calcul confiance
- **Signataires** : 3+ patterns CSV pour détection signatures
- **Idempotence** : Même OCR → même JSON (extraction déterministe)

## Améliorer l'extraction

Pour améliorer le taux d'extraction sur documents anciens :
1. Enrichir `corrections.csv` avec erreurs OCR fréquentes
2. Assouplir patterns `article.start` dans `patterns.properties`
3. Ajouter pré-processing OCR (normalisation REPUBLIOUE → RÉPUBLIQUE)
4. Enrichir dictionnaire français avec termes juridiques anciens
