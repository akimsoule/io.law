# Validation de Structure OCR

## Vue d'ensemble

Le module `law-qa` inclut désormais une validation complète de la structure des documents de loi OCR. Cette validation vérifie la présence des **5 parties obligatoires** d'un document légal béninois.

## Les 5 Parties Obligatoires

### 1. **Entête** (Header)
Composé de 3 éléments obligatoires :
- `RÉPUBLIQUE DU BENIN`
- `Fraternité-Justice-Travail` (devise)
- `PRÉSIDENCE DE LA RÉPUBLIQUE`

**Patterns détectés :**
```regex
R[ÉE]PUBLI(?:QUE|OUE)\s+DU\s+B[ÉE]NIN
Fraternit[ée]\s*-?\s*Justice\s*-?\s*Travail
PR[ÉE]SIDENCE\s+DE\s+LA\s+R[ÉE]PUBLI(?:QUE|OUE)
```

**Validation :** Les 3 éléments doivent être présents pour que l'entête soit considéré complet.

---

### 2. **Titre** (Title)
Commence toujours par `LOI N°` suivi du numéro et de la date.

**Exemple :**
```
LOI N° 2009-01 DU 16 JANVIER 2009
portant autorisation de ratification de l'Accord...
```

**Pattern détecté :**
```regex
LOI\s+N[°o]?\s*\d+
```

---

### 3. **Visa** (Legislative Approval)
Formule standard attestant de l'adoption par l'Assemblée Nationale.

**Commence par :**
```
L'Assemblée nationale a délibéré et adopté...
```

**Pattern détecté :**
```regex
L['']assembl[ée]e\s+nationale\s+a\s+d[ée]lib[ée]r[ée]
```

---

### 4. **Corps** (Body)
Le contenu législatif composé d'articles.

**Commence par :** Pattern article (Article 1er, Article 2, etc.)

**Se termine par l'une des formules :**
- `sera exécutée comme loi de l'État`
- `abroge toutes dispositions antérieures contraires`

**Pattern de fin détecté :**
```regex
(sera\s+ex[ée]cut[ée]e\s+comme\s+loi\s+de\s+l['']?[ÉE]tat)|
(abroge\s+toutes\s+dispositions\s+ant[ée]rieures\s+contraires)
```

---

### 5. **Pied** (Footer)
Le pied contient le lieu, la date et les signataires.

**Commence par :**
```
Fait à Cotonou, le 16 janvier 2009
```

**Se termine par :**
```
AMPLIATIONS: PR 6, AN 4, CC 2...
```

**Patterns détectés :**
```regex
Fait\s+[àa]\s+\w+        # Début
AMPLIATIONS?\s*:?        # Fin
```

**Validation :** Les deux patterns (Fait à + AMPLIATIONS) doivent être présents.

---

## Score de Validation

Le score est calculé simplement :
```
score = nombre_sections_présentes / 5
```

**Exemples :**
- 5/5 sections → score = 1.0 ✅ (parfait)
- 4/5 sections → score = 0.8 ⚠️ (bonne qualité)
- 3/5 sections → score = 0.6 ⚠️ (qualité moyenne)
- 2/5 sections → score = 0.4 ❌ (mauvaise qualité)
- 0/5 sections → score = 0.0 ❌ (échec complet)

---

## Utilisation

### API Java

```java
@Autowired
private OcrQualityService ocrQualityService;

public void validateDocument(String documentId) {
    // Charger le texte OCR
    String ocrText = Files.readString(
        Paths.get("data/ocr/loi/loi-2009-1.txt"), 
        StandardCharsets.UTF_8
    );
    
    // Valider la structure
    double structureScore = ocrQualityService.validateDocumentStructure(ocrText);
    
    // Interprétation
    if (structureScore == 1.0) {
        log.info("✅ Structure complète détectée");
    } else if (structureScore >= 0.8) {
        log.warn("⚠️ Structure presque complète : {}", structureScore);
    } else {
        log.error("❌ Structure incomplète : {}", structureScore);
    }
}
```

### Logs Détaillés

En mode `DEBUG`, les logs indiquent chaque section détectée ou manquante :

```
✅ Entête complet détecté (RÉPUBLIQUE + devise + PRÉSIDENCE)
✅ Titre détecté (LOI N°...)
✅ Visa détecté (L'assemblée nationale a délibéré...)
✅ Fin du corps détectée (sera exécutée comme loi / abroge...)
✅ Pied complet détecté (Fait à... + AMPLIATIONS)
📋 Structure document : 5/5 sections présentes → score=1.0
```

Ou en cas de problème :

```
✅ Entête complet détecté (RÉPUBLIQUE + devise + PRÉSIDENCE)
❌ Titre non détecté (LOI N°...)
✅ Visa détecté (L'assemblée nationale a délibéré...)
✅ Fin du corps détectée (sera exécutée comme loi / abroge...)
❌ Pied incomplet : Fait=true, AMPLIATIONS=false
📋 Structure document : 3/5 sections présentes → score=0.6
```

---

## Tests

### Tests Unitaires

10 tests unitaires couvrent tous les cas :

```bash
mvn test -pl law-tojson/law-qa -Dtest=OcrStructureValidationTest
```

**Tests inclus :**
1. ✅ Structure complète (5/5)
2. ⚠️ Entête manquant (4/5)
3. ⚠️ Titre manquant (4/5)
4. ⚠️ Visa manquant (4/5)
5. ⚠️ Fin du corps manquante (4/5)
6. ⚠️ Pied manquant (4/5)
7. ⚠️ Entête partiel (4/5)
8. ✅ Formule alternative "abroge..." (5/5)
9. ❌ Texte vide (0/5)
10. ❌ Texte null (0/5)

### Test d'Intégration

Test avec un vrai fichier OCR :

```bash
mvn test -pl law-tojson/law-qa -Dtest=OcrStructureValidationIntegrationTest
```

Utilise : `law-ocr-json/src/test/resources/samples_ocr/loi/loi-2009-1.txt`

---

## Tolérance OCR

Les patterns sont conçus pour tolérer les erreurs OCR courantes :

### Variantes de caractères acceptées

| Original | Variantes OCR acceptées |
|----------|------------------------|
| É | E, É |
| è | e, è |
| é | e, é |
| ' | ', ' (différents types d'apostrophes) |
| - | Peut être absent (espaces flexibles) |
| RÉPUBLIQUE | REPUBLIQUE, REPUBLI**O**UE |
| Q | O (confusion courante) |

### Case insensitive

Toutes les recherches sont **case-insensitive** pour tolérer :
- `REPUBLIQUE DU BENIN` → OK
- `Republique du Benin` → OK
- `republique du benin` → OK

---

## Intégration dans le Pipeline

Cette validation peut être intégrée dans `PdfToJsonProcessor` pour :

1. **Filtrer les OCR de mauvaise qualité** avant extraction
2. **Détecter les documents incomplets** sur le site SGG
3. **Prioriser les corrections OCR** (focus sur documents à faible score)

```java
// Exemple d'intégration
double structureScore = ocrQualityService.validateDocumentStructure(ocrText);

if (structureScore < 0.6) {
    log.warn("⚠️ Structure incomplète ({}), passage en mode IA", structureScore);
    return extractWithIA(document);
} else {
    log.info("✅ Structure OK ({}), extraction OCR", structureScore);
    return extractWithOcr(ocrText);
}
```

---

## Cas Limites

### Entête Partiel

Si seulement 1 ou 2 des 3 éléments de l'entête sont présents, l'entête est considéré **incomplet** :

```
REPUBLIQUE DU BENIN           ❌ (manque devise + PRÉSIDENCE)
Fraternité-Justice-Travail     
```
→ Score entête = 0

### Pied Partiel

Le pied nécessite **Fait à** ET **AMPLIATIONS** :

```
Fait à Cotonou, le 16 janvier 2009
Dr Boni YAYI
                              ❌ (manque AMPLIATIONS)
```
→ Score pied = 0

---

## Évolutions Futures

### Extraction de Métadonnées

En plus de la validation, extraire :
- Numéro de loi depuis le titre
- Date depuis le titre
- Lieu et date depuis le pied
- Liste des signataires

### Validation Sémantique

Vérifier la cohérence :
- Date titre = date pied
- Nombre d'articles annoncé = nombre réel
- Références croisées entre articles

### Scoring Avancé

Introduire des poids différents selon l'importance :
- Entête : 15%
- Titre : 25% (critique)
- Visa : 20%
- Corps : 30% (critique)
- Pied : 10%

---

**Date de création :** 11 décembre 2025  
**Version :** 1.0-SNAPSHOT  
**Module :** law-qa
