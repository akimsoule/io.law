package bj.gouv.sgg.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour UnrecognizedWordsService
 */
class UnrecognizedWordsServiceTest {

    @TempDir
    Path tempDir;

    private UnrecognizedWordsService service;
    private Path testFile;

    @BeforeEach
    void setUp() {
        testFile = tempDir.resolve("test_unrecognized.txt");
        service = new UnrecognizedWordsService();
        service.setWordFilePath(testFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(testFile)) {
            Files.delete(testFile);
        }
    }

    @Test
    void givenNewWordsWhenRecordThenFileCreatedWithWords() throws IOException {
        // Given
        Set<String> words = Set.of("erreur1", "erreur2", "erreur3");
        
        // When
        service.recordUnrecognizedWords(words, "test-doc-1");
        
        // Then
        assertTrue(Files.exists(testFile));
        List<String> lines = Files.readAllLines(testFile);
        assertEquals(3, lines.size());
        assertTrue(lines.contains("erreur1"));
        assertTrue(lines.contains("erreur2"));
        assertTrue(lines.contains("erreur3"));
    }

    @Test
    void givenExistingWordsWhenRecordSameWordsThenNoDuplicates() throws IOException {
        // Given
        Set<String> firstBatch = Set.of("word1", "word2");
        service.recordUnrecognizedWords(firstBatch, "doc-1");
        
        // When - re-record same words
        Set<String> secondBatch = Set.of("word1", "word2");
        service.recordUnrecognizedWords(secondBatch, "doc-2");
        
        // Then
        List<String> lines = Files.readAllLines(testFile);
        assertEquals(2, lines.size(), "Should not have duplicates");
    }

    @Test
    void givenExistingWordsWhenRecordNewWordsThenOnlyNewAdded() throws IOException {
        // Given
        Set<String> firstBatch = Set.of("word1", "word2");
        service.recordUnrecognizedWords(firstBatch, "doc-1");
        
        // When - add some new and some existing
        Set<String> secondBatch = Set.of("word2", "word3", "word4");
        service.recordUnrecognizedWords(secondBatch, "doc-2");
        
        // Then
        List<String> lines = Files.readAllLines(testFile);
        assertEquals(4, lines.size());
        assertTrue(lines.contains("word1"));
        assertTrue(lines.contains("word2"));
        assertTrue(lines.contains("word3"));
        assertTrue(lines.contains("word4"));
    }

    @Test
    void givenEmptySetWhenRecordThenNoFileCreated() {
        // Given
        Set<String> emptyWords = new HashSet<>();
        
        // When
        service.recordUnrecognizedWords(emptyWords, "doc-1");
        
        // Then
        assertFalse(Files.exists(testFile));
    }

    @Test
    void givenNullWordsWhenRecordThenNoException() {
        // When/Then - should not throw
        assertDoesNotThrow(() -> service.recordUnrecognizedWords(null, "doc-1"));
        assertFalse(Files.exists(testFile));
    }

    @Test
    void givenMultipleWordsWhenRecordThenAllSaved() throws IOException {
        // Given
        Set<String> words = Set.of("word1", "word2", "word3");
        
        // When
        service.recordUnrecognizedWords(words, "doc-1");
        
        // Then
        List<String> lines = Files.readAllLines(testFile);
        assertEquals(3, lines.size());
        assertTrue(lines.contains("word1"));
        assertTrue(lines.contains("word2"));
        assertTrue(lines.contains("word3"));
    }

    @Test
    void givenLowRateWhenCalculatePenaltyThenLowPenalty() {
        // Given
        double rate = 0.05; // 5%
        int total = 10;
        
        // When
        double penalty = service.calculateUnrecognizedPenalty(rate, total);
        
        // Then
        // ratePenalty: 0.05 * 2.0 = 0.10
        // countPenalty: min(0.2, 10/100.0 * 0.2) = min(0.2, 0.02) = 0.02
        // total: 0.10 + 0.02 = 0.12
        assertEquals(0.12, penalty, 0.01);
    }

    @Test
    void givenMediumRateWhenCalculatePenaltyThenMediumPenalty() {
        // Given
        double rate = 0.20; // 20%
        int total = 50;
        
        // When
        double penalty = service.calculateUnrecognizedPenalty(rate, total);
        
        // Then
        // ratePenalty: 0.2 + (0.20 - 0.10) * 1.5 = 0.2 + 0.15 = 0.35
        // countPenalty: min(0.2, 50/100.0 * 0.2) = min(0.2, 0.10) = 0.10
        // total: 0.35 + 0.10 = 0.45
        assertEquals(0.45, penalty, 0.01);
    }

    @Test
    void givenHighRateWhenCalculatePenaltyThenHighPenalty() {
        // Given
        double rate = 0.40; // 40%
        int total = 80;
        
        // When
        double penalty = service.calculateUnrecognizedPenalty(rate, total);
        
        // Then
        // ratePenalty: 0.5 + (0.40 - 0.30) * 1.5 = 0.5 + 0.15 = 0.65
        // countPenalty: min(0.2, 80/100.0 * 0.2) = min(0.2, 0.16) = 0.16
        // total: 0.65 + 0.16 = 0.81
        assertEquals(0.81, penalty, 0.01);
    }

    @Test
    void givenVeryHighRateWhenCalculatePenaltyThenCapped() {
        // Given
        double rate = 0.80; // 80%
        int total = 200;
        
        // When
        double penalty = service.calculateUnrecognizedPenalty(rate, total);
        
        // Then
        // Base: 0.8 + (0.80 - 0.50) * 0.4 = 0.8 + 0.12 = 0.92
        // Volume adjustment: +0.05 (>100) +0.05 (>200) = +0.10
        // Total: 0.92 + 0.10 = 1.02, capped at 1.0
        assertEquals(1.0, penalty, 0.01);
    }

    @Test
    void givenHighVolumeWhenCalculatePenaltyThenVolumeAdjustmentApplied() {
        // Given
        double rate = 0.15; // 15%
        int total = 150; // > 100
        
        // When
        double penalty = service.calculateUnrecognizedPenalty(rate, total);
        
        // Then
        // ratePenalty: 0.2 + (0.15 - 0.10) * 1.5 = 0.2 + 0.075 = 0.275
        // countPenalty: min(0.2, 150/100.0 * 0.2) = min(0.2, 0.20) = 0.20
        // total: 0.275 + 0.20 = 0.475
        assertEquals(0.475, penalty, 0.01);
    }

    @Test
    void givenVeryHighVolumeWhenCalculatePenaltyThenBothAdjustmentsApplied() {
        // Given
        double rate = 0.15; // 15%
        int total = 250; // > 200
        
        // When
        double penalty = service.calculateUnrecognizedPenalty(rate, total);
        
        // Then
        // ratePenalty: 0.2 + (0.15 - 0.10) * 1.5 = 0.275
        // countPenalty: min(0.2, 250/100.0 * 0.2) = min(0.2, 0.50) = 0.20 (capped)
        // total: 0.275 + 0.20 = 0.475
        assertEquals(0.475, penalty, 0.01);
    }

    @Test
    void givenZeroRateWhenCalculatePenaltyThenZero() {
        // Given
        double rate = 0.0;
        int total = 0;
        
        // When
        double penalty = service.calculateUnrecognizedPenalty(rate, total);
        
        // Then
        assertEquals(0.0, penalty, 0.001);
    }

    @Test
    void givenExistingFileWhenInitializeThenWordsLoaded() throws IOException {
        // Given - create file with existing words
        Files.writeString(testFile, "existing1\nexisting2\nexisting3\n");
        
        // When - initialize new service
        UnrecognizedWordsService newService = new UnrecognizedWordsService();
        newService.setWordFilePath(testFile);
        newService.loadExistingWords();
        
        // Then - add new words
        Set<String> newWords = Set.of("existing2", "new1");
        newService.recordUnrecognizedWords(newWords, "doc-1");
        
        List<String> lines = Files.readAllLines(testFile);
        assertEquals(4, lines.size()); // existing1, existing2, existing3, new1
    }

    @Test
    void givenConcurrentAccessWhenRecordThenThreadSafe() throws InterruptedException, IOException {
        // Given
        int threadCount = 10;
        int wordsPerThread = 100;
        Thread[] threads = new Thread[threadCount];
        
        // When - multiple threads recording words
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                Set<String> words = new HashSet<>();
                for (int j = 0; j < wordsPerThread; j++) {
                    words.add("word_" + threadId + "_" + j);
                }
                service.recordUnrecognizedWords(words, "doc-" + threadId);
            });
            threads[i].start();
        }
        
        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Then
        assertTrue(Files.exists(testFile));
        List<String> lines = Files.readAllLines(testFile);
        assertEquals(threadCount * wordsPerThread, lines.size());
    }
}
