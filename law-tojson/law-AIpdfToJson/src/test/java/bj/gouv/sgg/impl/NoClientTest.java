package bj.gouv.sgg.impl;

import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.exception.PromptLoadException;
import bj.gouv.sgg.model.LawDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NoClientTest {

    private NoClient noClient;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        noClient = new NoClient();
    }

    @Test
    void testTransform_ThrowsException() throws IOException {
        Path testPdf = tempDir.resolve("test.pdf");
        Files.writeString(testPdf, "%PDF-1.4");

        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .build();

        IAException exception = assertThrows(IAException.class, () -> 
            noClient.transform(document, testPdf)
        );

        assertTrue(exception.getMessage().contains("AI disabled"));
        assertTrue(exception.getMessage().contains("no provider available"));
    }

    @Test
    void testLoadPrompt_ThrowsException() {
        PromptLoadException exception = assertThrows(PromptLoadException.class, () -> 
            noClient.loadPrompt("pdf-parser.txt")
        );

        assertTrue(exception.getMessage().contains("AI disabled"));
        assertTrue(exception.getMessage().contains("no provider available"));
    }

    @Test
    void testTransform_WithNullPath() {
        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .build();

        assertThrows(IAException.class, () -> 
            noClient.transform(document, null)
        );
    }
}
