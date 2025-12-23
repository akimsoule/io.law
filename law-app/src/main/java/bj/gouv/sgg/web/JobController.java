package bj.gouv.sgg.web;

import bj.gouv.sgg.orchestrator.JobOrchestrator;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller pour déclencher les jobs Spring Batch via HTTP.
 *
 * Endpoints:
 * - POST /api/jobs/{jobName} avec body { type, documentId, maxDocuments }
 * - POST /api/pipeline/full avec body { type, documentId }
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class JobController {

    private final JobOrchestrator orchestrator;

    // Define constants for repeated literals
    private static final String STATUS = "status";
    private static final String MESSAGE = "message";

    public record JobRequest(
            @Pattern(regexp = "^(loi|decret)$", message = "type doit être 'loi' ou 'decret'") String type,
            String documentId,
            String maxDocuments
    ) {}

    @PostMapping("/jobs/fetchCurrent")
    public ResponseEntity<Map<String, String>> fetchCurrent(@RequestBody JobRequest request) {
        return runJob("fetchCurrentJob", request);
    }

    @PostMapping("/jobs/fetchPrevious")
    public ResponseEntity<Map<String, String>> fetchPrevious(@RequestBody JobRequest request) {
        return runJob("fetchPreviousJob", request);
    }

    @PostMapping("/jobs/download")
    public ResponseEntity<Map<String, String>> download(@RequestBody JobRequest request) {
        return runJob("downloadJob", request);
    }

    @PostMapping("/jobs/ocr")
    public ResponseEntity<Map<String, String>> ocr(@RequestBody JobRequest request) {
        return runJob("ocrJob", request);
    }

    @PostMapping("/jobs/ocrJson")
    public ResponseEntity<Map<String, String>> ocrJson(@RequestBody JobRequest request) {
        return runJob("ocrJsonJob", request);
    }

    @PostMapping("/jobs/jsonConversion")
    public ResponseEntity<Map<String, String>> jsonConversion(@RequestBody JobRequest request) {
        return runJob("jsonConversionJob", request);
    }

    @PostMapping("/jobs/consolidate")
    public ResponseEntity<Map<String, String>> consolidate(@RequestBody JobRequest request) {
        return runJob("consolidateJob", request);
    }

    @PostMapping("/pipeline/full")
    public ResponseEntity<Map<String, String>> fullPipeline(@Valid @RequestBody JobRequest request) {
        try {
            String type = request.type() == null ? "loi" : request.type();
            String documentId = request.documentId();
            orchestrator.runFullPipeline(type, documentId);
            return ResponseEntity.ok(Map.of(STATUS, "OK", MESSAGE, "Pipeline executed"));
        } catch (Exception e) {
            log.error("Erreur pipeline full: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(STATUS, "ERROR", MESSAGE, e.getMessage()));
        }
    }

    private ResponseEntity<Map<String, String>> runJob(String jobName, @Valid @RequestBody JobRequest request) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("type", request.type() == null ? "loi" : request.type());
            if (request.documentId() != null) params.put("documentId", request.documentId());
            if (request.maxDocuments() != null) params.put("maxDocuments", request.maxDocuments());

            orchestrator.runJob(jobName, params);
            return ResponseEntity.ok(Map.of(STATUS, "OK", "job", jobName));
        } catch (Exception e) {
            log.error("Erreur lancement job {}: {}", jobName, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(STATUS, "ERROR", MESSAGE, e.getMessage()));
        }
    }
}
