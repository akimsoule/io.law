package bj.gouv.sgg.service.extract;

/**
 * Service principal pour l'extraction OCR
 * Combine ExtractArticles, ExtractMetadata et CalculateConfidence
 */
public interface OcrExtractionService extends ExtractArticles, ExtractMetadata, CalculateConfidence {
    // Interface composite pour regrouper toutes les capacités d'extraction OCR
}
