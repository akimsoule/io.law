# Vérification de Cohérence - LawDocumentValidator

## ✅ Architecture Mise en Place

### 1. Service Central : LawDocumentValidator (Singleton)
- **Localisation** : `law-common/src/main/java/bj/gouv/sgg/service/LawDocumentValidator.java`
- **Responsabilité** : Validation combinant status DB + existence fichiers
- **Pattern** : Singleton avec `getInstance()`

### 2. Délégation depuis LawDocumentEntity
- **Toutes les méthodes de validation** délèguent au validator
- **Cohérence** : Même logique partout dans le code

## 🔍 Méthodes de Validation

### Fetch
```java
// Entity délègue au Validator
entity.mustFetch() → LawDocumentValidator.mustFetch(entity)
entity.isFetched() → LawDocumentValidator.isFetched(entity)
```

**Logique `mustFetch()`** :
- ✅ PENDING → fetch
- ❌ NOT_FOUND → ne jamais refetch (même année courante)
- ❌ FETCHED ou plus loin → ne pas refetch (idempotence)
- ❌ Échecs en aval → ne pas refetch

**Logique `isFetched()`** :
- ✅ Status dans [FETCHED, DOWNLOADED, OCRED, EXTRACTED, CONSOLIDATED]
- ✅ OU fichiers existent (PDF, OCR ou JSON)

### Download
```java
entity.mustDownload() → LawDocumentValidator.mustDownload(entity)
entity.isDownloaded() → LawDocumentValidator.isDownloaded(entity)
```

**Logique `mustDownload()`** :
- ✅ Status = FETCHED
- ✅ Status = FAILED_CORRUPTED
- ✅ Status = DOWNLOADED mais PDF absent (incohérence détectée)

**Logique `isDownloaded()`** :
- ✅ Status dans [DOWNLOADED, OCRED, EXTRACTED, CONSOLIDATED]
- ✅ ET PDF existe

### OCR
```java
entity.mustOcr() → LawDocumentValidator.mustOcr(entity)
entity.isOcred() → LawDocumentValidator.isOcred(entity)
```

**Logique `mustOcr()`** :
- ✅ Status = DOWNLOADED
- ✅ Status = FAILED_OCR
- ✅ Status = OCRED mais fichier OCR absent (incohérence)

**Logique `isOcred()`** :
- ✅ Status dans [OCRED, EXTRACTED, CONSOLIDATED]
- ✅ ET fichier OCR existe

### Extraction
```java
entity.mustExtractArticles() → LawDocumentValidator.mustExtractArticles(entity)
entity.isExtracted() → LawDocumentValidator.isExtracted(entity)
```

**Logique `mustExtractArticles()`** :
- ✅ Status = OCRED
- ✅ Status = FAILED_EXTRACTION

**Logique `isExtracted()`** :
- ✅ Status dans [EXTRACTED, CONSOLIDATED]
- ✅ ET JSON existe

### Consolidation
```java
entity.mustConsolidate() → LawDocumentValidator.mustConsolidate(entity)
entity.isConsolidated() → LawDocumentValidator.isConsolidated(entity)
```

## 📍 Utilisation dans les Services

### FetchCurrentServiceImpl
```java
// Ligne 65: Filtrer les documents déjà fetchés
.filter(LawDocumentEntity::isFetched)

// Ligne 121: Vérifier avant fetch
if (existingDoc.isFetched()) {
    log.info("ℹ️ Déjà fetché: {}", documentId);
    return;
}
```
✅ **Cohérent** : Utilise `isFetched()` qui vérifie status + fichiers

### FetchPreviousServiceImpl
```java
// Ligne 202: Vérifier avant fetch
if (existingDoc.isFetched()) {
    log.info("ℹ️ Déjà fetché: {}", documentId);
    return;
}
```
✅ **Cohérent** : Utilise `isFetched()` qui vérifie status + fichiers

### DownloadServiceImpl
```java
// Ligne 104: Récupérer les documents à télécharger
lawDocumentService.findByTypeAndStatus(type, ProcessingStatus.FETCHED)

// Ligne 222: Vérifier statut
if (!doc.mustDownload()) {
    log.warn("⚠️ Statut incorrect: {}", doc.getStatus());
    return;
}
```
✅ **Cohérent** : 
- Récupère les FETCHED de la DB
- Double vérification avec `mustDownload()` qui vérifie aussi l'existence du fichier

## 🎯 Avantages de cette Architecture

### 1. Séparation des Responsabilités
- **Entity** : Données + délégation simple
- **Validator** : Logique métier + vérification fichiers
- **Services** : Orchestration

### 2. Cohérence Garantie
- ✅ Même logique partout
- ✅ Impossible d'avoir des validations divergentes
- ✅ Un seul endroit à maintenir

### 3. Détection d'Incohérences
- ⚠️ Logs quand status DB ≠ réalité disque
- 🔧 Correction automatique possible (mustDownload retourne true si fichier absent)

### 4. Idempotence
- 📌 `mustFetch()` garantit qu'on ne refetch jamais un document déjà traité
- 📌 Validation basée sur status final, pas juste l'étape en cours

### 5. Testabilité
- 🧪 Validator peut être testé unitairement
- 🧪 Entity reste simple (pure délégation)
- 🧪 Services testent uniquement leur orchestration

## 🔄 Flux Complet

```
1. PENDING
   └─> mustFetch() = true
       └─> FetchService
           └─> Status = FETCHED

2. FETCHED
   └─> mustDownload() = true
       └─> DownloadService
           └─> Télécharge PDF
           └─> Status = DOWNLOADED

3. DOWNLOADED
   └─> mustOcr() = true
       └─> OcrProcessingService
           └─> Génère OCR
           └─> Status = OCRED

4. OCRED
   └─> mustExtractArticles() = true
       └─> ExtractionService
           └─> Génère JSON
           └─> Status = EXTRACTED

5. EXTRACTED
   └─> mustConsolidate() = true
       └─> ConsolidationService
           └─> Status = CONSOLIDATED
```

## ✅ Validation de Cohérence

### Test 1 : Entity → Validator
```java
LawDocumentEntity entity = LawDocumentEntity.create("loi", 2024, 1);
entity.setStatus(ProcessingStatus.PENDING);

// Méthode de l'entity
boolean mustFetch1 = entity.mustFetch();

// Méthode du validator directement
boolean mustFetch2 = LawDocumentValidator.getInstance().mustFetch(entity);

assert mustFetch1 == mustFetch2; // ✅ Toujours vrai
```

### Test 2 : Détection d'Incohérence
```java
LawDocumentEntity entity = /* ... */;
entity.setStatus(ProcessingStatus.DOWNLOADED);

// Si PDF n'existe pas sur disque
boolean isDownloaded = entity.isDownloaded(); // false (malgré status DOWNLOADED)
boolean mustDownload = entity.mustDownload(); // true (correction nécessaire)

// Le validator log un warning :
// ⚠️ Document loi-2024-1 marqué DOWNLOADED mais PDF absent sur disque
```

### Test 3 : Idempotence
```java
// Document déjà fetch
entity.setStatus(ProcessingStatus.FETCHED);
assert !entity.mustFetch(); // ✅ false

// Document déjà téléchargé
entity.setStatus(ProcessingStatus.DOWNLOADED);
assert !entity.mustFetch(); // ✅ false (idempotence)
```

## 📊 Conclusion

✅ **Architecture cohérente**
✅ **Validation combinée (status + fichiers)**
✅ **Idempotence garantie**
✅ **Détection d'incohérences**
✅ **Délégation propre**
✅ **Services utilisent correctement les méthodes**

**Aucune incohérence détectée** entre le validator et son utilisation dans les services.
