# Law JSON Config

Module orchestrateur pour la conversion PDF → JSON.

## Responsabilité

Orchestrer la pipeline de conversion :
1. **PDF → OCR** via `law-pdf-ocr`
2. **OCR → JSON** via `law-ocr-json`

## Architecture

Module de configuration Spring Batch pure (pas d'application standalone).
Définit uniquement le `Job` orchestrateur qui chaîne les steps des modules dépendants.

## Dépendances

- **law-common** : Entités, repositories, configuration
- **law-pdf-ocr** : Extraction OCR des PDFs
- **law-ocr-json** : Conversion OCR vers JSON structuré

## Configuration

### Job : `jsonConversionJob`

```java
@Bean
public Job jsonConversionJob(
        JobRepository jobRepository,
        Step pdfToOcrStep,      // de law-pdf-ocr
        Step ocrToJsonStep) {   // de law-ocr-json
    
    return new JobBuilder("jsonConversionJob", jobRepository)
            .start(pdfToOcrStep)
            .next(ocrToJsonStep)
            .build();
}
```

## Extensions futures

- **law-ai** : Enrichissement par IA (Ollama/Groq)
- **law-qa** : Validation qualité des données

Ces modules seront ajoutés comme étapes supplémentaires dans la chaîne.
