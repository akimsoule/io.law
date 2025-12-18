package bj.gouv.sgg.cli;

import bj.gouv.sgg.job.FetchJob;
import bj.gouv.sgg.job.download.DownloadJob;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Exécuteur de jobs individuels.
 * Centralise la logique d'instanciation et d'exécution des jobs.
 */
@Slf4j
final class JobRunner {

    private JobRunner() {}

    static void fetchCurrent(String type) {
        log.info("🔍 Fetch Current Year ({})", type);
        FetchJob job = new FetchJob();
        try {
            job.runCurrent(type);
        } finally {
            job.shutdown();
        }
    }

    static void fetchPrevious(String type, Map<String, String> params) {
        int maxItems = Integer.parseInt(params.getOrDefault("maxItems", "100"));
        log.info("🔍 Fetch Previous Years ({}, max={})", type, maxItems);
        FetchJob job = new FetchJob();
        try {
            job.runPrevious(type, maxItems);
        } finally {
            job.shutdown();
        }
    }

    static void download(String type, Map<String, String> params) {
        log.info("⬇️  Download PDFs ({})", type);
        DownloadJob job = new DownloadJob();
        try {
            job.run(type);
        } finally {
            job.shutdown();
        }
    }

    static void ocr(String type) {
        log.info("🔄 OCR Extraction ({})", type);
        bj.gouv.sgg.job.OcrJob job = new bj.gouv.sgg.job.OcrJob();
        try {
            job.run(type);
        } finally {
            job.shutdown();
        }
    }

    static void extract(String type) {
        log.info("📄 Article Extraction ({})", type);
        bj.gouv.sgg.job.ArticleExtractionJob job = new bj.gouv.sgg.job.ArticleExtractionJob();
        try {
            job.run(type);
        } finally {
            job.shutdown();
        }
    }

    static void validate(String type) {
        log.info("✅ Quality Validation ({})", type);
        bj.gouv.sgg.job.ValidationJob job = new bj.gouv.sgg.job.ValidationJob();
        job.run(type);
    }

    static void ia(String type) {
        log.info("🤖 IA Extraction ({})", type);
        bj.gouv.sgg.job.IAExtractionJob job = new bj.gouv.sgg.job.IAExtractionJob();
        job.run(type);
    }
    
    static void consolidate(String type) {
        log.info("💾 Consolidation ({})", type);
        bj.gouv.sgg.job.ConsolidateJob job = new bj.gouv.sgg.job.ConsolidateJob();
        job.run(type);
    }


    static void fullPipeline(String docId) {
        log.info("🚀 Full Pipeline pour document ciblé: {}", docId);
        
        // Parse docId
        String[] parts = docId.split("-");
        if (parts.length != 3) {
            log.error("❌ Format invalide: {}. Attendu: loi-2024-15", docId);
            return;
        }
        
        // Étape 1: Fetch
        log.info("📋 Étape 1/6: Fetch document {}", docId);
        FetchJob fetchJob = new FetchJob();
        try {
            fetchJob.runDocument(docId);
        } finally {
            fetchJob.shutdown();
        }
        
        // Étape 2: Download
        log.info("📋 Étape 2/6: Download PDF {}", docId);
        DownloadJob downloadJob = new DownloadJob();
        try {
            downloadJob.runDocument(docId);
        } finally {
            downloadJob.shutdown();
        }
        
        // Étape 3: OCR
        log.info("📋 Étape 3/6: OCR extraction {}", docId);
        new bj.gouv.sgg.job.OcrJob().runDocument(docId);
        
        // Étape 4: Extract
        log.info("📋 Étape 4/6: Article extraction {}", docId);
        new bj.gouv.sgg.job.ArticleExtractionJob().runDocument(docId);
        
        // Étape 5: Validate
        log.info("📋 Étape 5/6: Quality validation {}", docId);
        new bj.gouv.sgg.job.ValidationJob().runDocument(docId);
        
        // Étape 6: IA
        log.info("📋 Étape 6/6: IA enhancement {}", docId);
        new bj.gouv.sgg.job.IAExtractionJob().runDocument(docId);
        
        log.info("✅ Pipeline terminé pour {}", docId);
    }

    static void orchestrate(String type, Map<String, String> params) {
        log.info("🚀 Orchestration Complète ({})", type);
        
        boolean skipFetchCurrent = "true".equals(params.get("skipFetchCurrent"));
        
        logSeparator("Étape 1/6: Fetch Current + Previous");
        if (skipFetchCurrent) {
            log.info("⏭️  Fetch Current skippé (skipFetchCurrent=true)");
        } else {
            fetchCurrent(type);
        }
        fetchPrevious(type, params);
        
        logSeparator("Étape 2/6: Download PDFs");
        download(type, params);
        
        logSeparator("Étape 3/6: OCR Extraction");
        ocr(type);
        
        logSeparator("Étape 4/5: Article Extraction");
        extract(type);
        
        logSeparator("Étape 5/5: Consolidation");
        consolidate(type);
        
        log.info("✅ Pipeline complet terminé pour type: {}", type);
    }

    private static void logSeparator(String message) {
        log.info("═══════════════════════════════════════════════════════════");
        log.info(message);
        log.info("═══════════════════════════════════════════════════════════");
    }
}
