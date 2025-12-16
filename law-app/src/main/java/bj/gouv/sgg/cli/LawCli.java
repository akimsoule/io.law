package bj.gouv.sgg.cli;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Entrée CLI complète sans Spring.
 * Usage:
 *   java -jar law-app.jar --job=fetch --type=loi
 *   java -jar law-app.jar --job=ocr --type=loi
 *   java -jar law-app.jar --job=fullJob --doc=loi-2024-15
 *   java -jar law-app.jar --job=orchestrate --type=loi
 */
@Slf4j
public final class LawCli {

    private static final String SEPARATOR = "═══════════════════════════════════════════════════════════";

    public static void main(String[] args) {
        // Configurer cache PDFBox temporaire (évite scan polices système macOS)
        String tmpDir = System.getProperty("java.io.tmpdir");
        System.setProperty("pdfbox.fontcache", tmpDir + "/pdfbox-cache-" + System.currentTimeMillis());
        
        try {
            Map<String, String> params = parseArgs(args);
            String job = params.getOrDefault("job", "orchestrate");
            String type = params.getOrDefault("type", "loi");
            
            logHeader(job, type);
            long startTime = System.currentTimeMillis();
            
            routeJob(job, type, params);
            
            logFooter(System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            log.error("❌ Erreur: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private static void routeJob(String job, String type, Map<String, String> params) {
        switch (job) {
            case "fetchCurrent":
            case "fetchCurrentJob":
            case "fetch":
                JobRunner.fetchCurrent(type, params);
                break;
            case "fetchPrevious":
            case "fetchPreviousJob":
                JobRunner.fetchPrevious(type, params);
                break;
            case "download":
            case "downloadJob":
                JobRunner.download(type, params);
                break;
            case "ocr":
            case "ocrJob":
                JobRunner.ocr(type);
                break;
            case "extract":
            case "articleExtractionJob":
                JobRunner.extract(type);
                break;
            case "validate":
            case "validationJob":
                JobRunner.validate(type);
                break;
            case "ia":
            case "iaJob":
            case "iaExtractionJob":
                JobRunner.ia(type);
                break;
            case "fullJob":
                runFullJob(params);
                break;
            case "orchestrate":
            default:
                JobRunner.orchestrate(type, params);
                break;
        }
    }

    private static void runFullJob(Map<String, String> params) {
        String docId = params.get("doc");
        if (docId == null || docId.isEmpty()) {
            log.error("❌ ERREUR: Le paramètre --doc est obligatoire pour fullJob");
            log.info("Usage: java -jar law-app.jar --job=fullJob --doc=loi-2024-15");
            return;
        }
        JobRunner.fullPipeline(docId);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        Arrays.stream(args).forEach(a -> {
            if (a.startsWith("--") && a.contains("=")) {
                String[] kv = a.substring(2).split("=", 2);
                map.put(kv[0], kv.length > 1 ? kv[1] : "");
            } else if (a.startsWith("--")) {
                map.put(a.substring(2), "true");
            }
        });
        return map;
    }

    private static void logHeader(String job, String type) {
        log.info(SEPARATOR);
        log.info("🚀 io.law CLI (Sans Spring)");
        log.info(SEPARATOR);
        log.info("📋 Job: {}", job);
        log.info("🎯 Type: {}", type);
        log.info(SEPARATOR);
    }

    private static void logFooter(long elapsedMs) {
        log.info(SEPARATOR);
        log.info("✅ Terminé en {} ms", elapsedMs);
        log.info(SEPARATOR);
    }
}
