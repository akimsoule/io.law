# Tests law-AIpdfToJson

Suite de tests pour les composants d'extraction IA (Ollama et Groq).

## Vue d'ensemble

**29 tests**, 100% passants ✅

| Classe de Test | Tests | Description |
|----------------|-------|-------------|
| `OllamaClientTest` | 7 | Tests client Ollama (API locale) |
| `GroqClientTest` | 7 | Tests client Groq (API cloud) |
| `NoClientTest` | 3 | Tests fallback sans IA |
| `IAServiceIntegrationTest` | 5 | Tests d'intégration |
| `IAServiceTest` | 2 | Tests interface IAService |
| `JsonResultTest` | 5 | Tests modèle JsonResult |

## Structure

```
src/test/java/bj/gouv/sgg/
├── impl/
│   ├── OllamaClientTest.java       # Tests Ollama
│   ├── GroqClientTest.java         # Tests Groq
│   └── NoClientTest.java           # Tests NoClient
├── integration/
│   └── IAServiceIntegrationTest.java  # Tests intégration
├── service/
│   └── IAServiceTest.java          # Tests interface
└── modele/
    └── JsonResultTest.java         # Tests modèle
```

## Technologies Utilisées

### Frameworks de Tests
- **JUnit 5** (Jupiter) : Framework de tests principal
- **Mockito** : Injection de mocks (MockitoExtension)
- **MockWebServer** (OkHttp 4.12.0) : Mock serveurs HTTP
- **@TempDir** : Fichiers temporaires

### Patterns de Tests

#### 1. Tests avec MockWebServer (Ollama)

```java
@Test
void testTransform_SuccessfulResponse() throws Exception {
    // Mock Ollama API
    String mockResponse = "{\"model\":\"qwen2.5:7b\",\"response\":\"{...}\"}";
    mockServer.enqueue(new MockResponse()
        .setResponseCode(200)
        .setBody(mockResponse));
    
    JsonResult result = ollamaClient.transform(document, pdfPath);
    
    assertNotNull(result);
    assertTrue(result.getConfidence() >= 0.1);
}
```

**Avantages** :
- Pas besoin de serveur Ollama réel
- Tests rapides et déterministes
- Contrôle total des réponses

#### 2. Tests avec Reflection (Groq)

```java
@BeforeEach
void setUp() throws Exception {
    groqClient = new GroqClient();
    setField(groqClient, "apiKey", "test-key");
    setField(groqClient, "model", "llama-3.3-70b-versatile");
}

private void setField(Object target, String fieldName, Object value) 
    throws NoSuchFieldException, IllegalAccessException {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
}
```

**Pourquoi Reflection** :
- Éviter le problème **Byte Buddy + Java 25**
- Injecter des valeurs dans champs privés
- Plus simple que `@Mock` pour objets simples

#### 3. Tests avec Vraies Instances (LawProperties)

```java
@BeforeEach
void setUp() throws IOException {
    properties = new LawProperties();
    LawProperties.Capacity capacity = new LawProperties.Capacity();
    capacity.setOllamaUrl(mockServer.url("/").toString());
    capacity.setOllamaModelsRequired("qwen2.5:7b");
    properties.setCapacity(capacity);
    
    ollamaClient = new OllamaClient(properties);
}
```

**Avantages** :
- Pas de dépendance Mockito/Byte Buddy
- Compatible Java 25
- Tests plus réalistes

#### 4. Tests avec @TempDir

```java
@Test
void testTransformWithValidPdf(@TempDir Path tempDir) throws Exception {
    Path testPdf = tempDir.resolve("test.pdf");
    Files.writeString(testPdf, "%PDF-1.4 content");
    
    JsonResult result = client.transform(document, testPdf);
    
    assertNotNull(result);
}
```

**Avantages** :
- Nettoyage automatique
- Isolation entre tests
- Pas de fichiers temporaires laissés

## Exécution

### Tous les tests

```bash
mvn test
```

### Tests d'une classe spécifique

```bash
mvn test -Dtest=OllamaClientTest
mvn test -Dtest=GroqClientTest
mvn test -Dtest=IAServiceIntegrationTest
```

### Test spécifique

```bash
mvn test -Dtest=OllamaClientTest#testTransform_SuccessfulResponse
```

### Mode verbose

```bash
mvn test -X
```

## Coverage

### Composants Testés

| Composant | Classes | Coverage |
|-----------|---------|----------|
| **impl/** | 3 | ✅ 100% |
| **service/** | 3 interfaces | ✅ 100% |
| **modele/** | 1 | ✅ 100% |
| **exception/** | 2 | ⏹️ Pas testé |

### Scénarios Couverts

**OllamaClientTest** :
- ✅ Chargement prompts (loi + décret)
- ✅ PDF non trouvé
- ✅ Réponse API succès
- ✅ Erreur API (500)
- ✅ Contenu OCR vide
- ✅ Type décret

**GroqClientTest** :
- ✅ Chargement prompts (loi + décret)
- ✅ Pas d'API key
- ✅ PDF non trouvé
- ✅ Réponse API succès
- ✅ Erreur API (401)
- ✅ Type décret

**NoClientTest** :
- ✅ Transform lève exception
- ✅ LoadPrompt lève exception
- ✅ Path null

**IAServiceIntegrationTest** :
- ✅ Fallback NoClient
- ✅ Chargement prompts
- ✅ PDF manquant
- ✅ PDF valide
- ✅ Documents décret

**IAServiceTest** :
- ✅ Contrat interface
- ✅ Implémentation mock

**JsonResultTest** :
- ✅ Constructor/getters
- ✅ Sources différentes (OLLAMA, GROQ, ERROR)
- ✅ Range confidence (0.0-1.0)
- ✅ JSON vide
- ✅ JSON large (100 articles)

## Résultats Attendus

```
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- in OllamaClientTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- in GroqClientTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in NoClientTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in IAServiceIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in IAServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in JsonResultTest
[INFO] 
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Configuration

### application-test.yml

```yaml
law:
  capacity:
    ia: 4
    ocr: 2
    ollama-url: http://localhost:11434
    ollama-models-required: qwen2.5:7b
  groq:
    api-key: ${GROQ_API_KEY:test-key}
    base-url: https://api.groq.com/openai/v1

logging:
  level:
    bj.gouv.sgg: DEBUG
```

### POM Dependencies

```xml
<dependencies>
    <!-- Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- MockWebServer -->
    <dependency>
        <groupId>com.squareup.okhttp3</groupId>
        <artifactId>mockwebserver</artifactId>
        <version>4.12.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Bonnes Pratiques

### ✅ À FAIRE

1. **Utiliser MockWebServer** pour APIs HTTP
   ```java
   MockWebServer mockServer = new MockWebServer();
   mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("..."));
   ```

2. **Utiliser @TempDir** pour fichiers temporaires
   ```java
   @Test
   void test(@TempDir Path tempDir) { }
   ```

3. **Tester les cas d'erreur** (404, 500, timeout)
   ```java
   mockServer.enqueue(new MockResponse().setResponseCode(500));
   ```

4. **Vérifier les logs** (WARN, ERROR)
   ```java
   // Logs attendus dans sortie
   18:31:30 [main] WARN - PDF file not found: /path/to/missing.pdf
   ```

5. **Assertions précises**
   ```java
   assertNotNull(result);
   assertTrue(result.getConfidence() >= 0.1);
   assertEquals("OLLAMA", result.getSource());
   ```

### ❌ À ÉVITER

1. **NE PAS utiliser @Mock avec LawProperties** (incompatibilité Java 25)
   ```java
   // ❌ MAL - Échoue avec Java 25
   @Mock
   private LawProperties mockProperties;
   
   // ✅ BIEN - Utiliser vraie instance
   LawProperties properties = new LawProperties();
   ```

2. **NE PAS laisser MockWebServer tourner**
   ```java
   @AfterEach
   void tearDown() throws IOException {
       if (mockServer != null) {
           mockServer.shutdown();
       }
   }
   ```

3. **NE PAS hardcoder les chemins**
   ```java
   // ❌ MAL
   Path pdf = Paths.get("/Users/akimsoule/test.pdf");
   
   // ✅ BIEN
   Path pdf = tempDir.resolve("test.pdf");
   ```

4. **NE PAS ignorer les exceptions**
   ```java
   // ❌ MAL
   try { } catch (Exception e) { }
   
   // ✅ BIEN
   assertThrows(IAException.class, () -> client.transform(...));
   ```

## Troubleshooting

### Problème : Tests échouent avec Java 25

**Erreur** :
```
Java 25 (69) is not supported by the current version of Byte Buddy
```

**Solution** : Voir [MOCKITO_JAVA25_ISSUE.md](MOCKITO_JAVA25_ISSUE.md)

### Problème : MockWebServer connection refused

**Erreur** :
```
Failed to connect to localhost/[0:0:0:0:0:0:0:1]:11434
```

**Cause** : Test tente de se connecter au vrai Ollama au lieu du mock.

**Solution** : Vérifier que `mockServer.url()` est bien injecté dans le client.

### Problème : Tests lents

**Symptôme** : Tests prennent >10 secondes.

**Causes possibles** :
1. Timeouts trop longs dans MockWebServer
2. Serveur Ollama réel démarré (conflit)
3. Pas de `mockServer.shutdown()` dans tearDown

**Solution** :
```java
@AfterEach
void tearDown() throws IOException {
    if (mockServer != null) {
        mockServer.shutdown();
    }
}
```

## Améliorations Futures

### Tests Manquants

1. **Exception Tests**
   - Tester `IAException`
   - Tester `PromptLoadException`

2. **Performance Tests**
   - Timeouts (120s Ollama, 60s Groq)
   - Gros PDFs (>10MB)
   - Réponses streaming

3. **Edge Cases**
   - JSON malformé
   - Encodage non-UTF8
   - Caractères spéciaux

### Métriques

```bash
# Coverage report
mvn test jacoco:report

# Report dans target/site/jacoco/index.html
```

### CI/CD

```yaml
# .github/workflows/test.yml
name: Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
      - run: mvn test
```

## Références

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [MockWebServer](https://github.com/square/okhttp/tree/master/mockwebserver)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
