package bj.gouv.sgg.cli;

import bj.gouv.sgg.job.download.DownloadJob;
import bj.gouv.sgg.job.fetch.FetchJob;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Entrée CLI complète sans Spring.
 * Usage:
 *   java -jar law-app.jar --job=fetch --type=loi
 *   java -jar law-app.jar --job=orchestrate --type=loi
 */
public final class LawCli {

    public static void main(String[] args) {
        try {
            Map<String, String> params = parseArgs(args);
            String job = params.getOrDefault("job", "orchestrate");
            String type = params.getOrDefault("type", "loi");
            
            System.out.printf("═══════════════════════════════════════════════════════════%n");
            System.out.printf("🚀 io.law CLI (Sans Spring)%n");
            System.out.printf("═══════════════════════════════════════════════════════════%n");
            System.out.printf("📋 Job: %s%n", job);
            System.out.printf("🎯 Type: %s%n", type);
            System.out.printf("═══════════════════════════════════════════════════════════%n%n");

            long startTime = System.currentTimeMillis();
            
            switch (job) {
                case "fetchCurrent":
                case "fetch":
                    runFetchCurrent(type, params);
                    break;
                case "fetchPrevious":
                    runFetchPrevious(type, params);
                    break;
                case "download":
                case "downloadJob":
                    runDownload(type, params);
                    break;
                case "ocr":
                case "ocrJob":
                    runOcr(type, params);
                    break;
                case "extract":
                case "articleExtractionJob":
                    runExtract(type, params);
                    break;
                case "consolidate":
                case "consolidateJob":
                    runConsolidate(type, params);
                    break;
                case "fix":
                case "fixJob":
                    runFix(type, params);
                    break;
                case "orchestrate":
                default:
                    runOrchestrate(type, params);
                    break;
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.printf("%n═══════════════════════════════════════════════════════════%n");
            System.out.printf("✅ Terminé en %d ms%n", elapsed);
            System.out.printf("═══════════════════════════════════════════════════════════%n");
            
        } catch (Exception e) {
            System.err.printf("❌ Erreur: %s%n", e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
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

    private static void runFetchCurrent(String type, Map<String, String> params) {
        System.out.printf("🔍 Fetch Current Year (%s)%n%n", type);
        FetchJob job = new FetchJob();
        try {
            job.runCurrent(type);
        } finally {
            job.shutdown();
        }
    }

    private static void runFetchPrevious(String type, Map<String, String> params) {
        int maxItems = Integer.parseInt(params.getOrDefault("maxItems", "100"));
        System.out.printf("🔍 Fetch Previous Years (%s, max=%d)%n%n", type, maxItems);
        FetchJob job = new FetchJob();
        try {
            job.runPrevious(type, maxItems);
        } finally {
            job.shutdown();
        }
    }

    private static void runDownload(String type, Map<String, String> params) {
        int maxDocs = Integer.parseInt(params.getOrDefault("maxDocuments", "0"));
        System.out.printf("⬇️  Download PDFs (%s, max=%d)%n%n", type, maxDocs);
        DownloadJob job = new DownloadJob();
        try {
            job.run(type, maxDocs);
        } finally {
            job.shutdown();
        }
    }

    private static void runOcr(String type, Map<String, String> params) {
        System.out.printf("🔄 OCR Extraction (%s) — à implémenter%n", type);
        // TODO: PDFBox + Tesseract (JavaCPP) vers .txt
    }

    private static void runExtract(String type, Map<String, String> params) {
        System.out.printf("📄 Article Extraction (%s) — à implémenter%n", type);
        // TODO: Parsing OCR → JSON (regex)
    }

    private static void runConsolidate(String type, Map<String, String> params) {
        System.out.printf("🗄️  Consolidation (%s) — à implémenter%n", type);
        // TODO: Consolidation dans JSON/CSV ou JDBC MySQL minimal
    }

    private static void runFix(String type, Map<String, String> params) {
        System.out.printf("🛠️  Fix Missing Files (%s) — à implémenter%n", type);
        // TODO: Détection et correction des statuts/ fichiers manquants
    }

    private static void runOrchestrate(String type, Map<String, String> params) {
        System.out.printf("🚀 Orchestration Complète (%s)%n%n", type);
        
        runFetchCurrent(type, params);
        System.out.println();
        
        runFetchPrevious(type, params);
        System.out.println();
        
        runDownload(type, params);
        System.out.println();
        
        runOcr(type, params);
        System.out.println();
        
        runExtract(type, params);
        System.out.println();
        
        runConsolidate(type, params);
        System.out.println();
        
        runFix(type, params);
    }
}
