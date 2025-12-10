package bj.gouv.sgg.service;

import bj.gouv.sgg.config.LawProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Tests unitaires pour FileStorageService.
 */

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // Permet les stubs inutilisés pour certains tests
class FileStorageServiceTest {

    @Mock
    private LawProperties lawProperties;

    @Mock
    private LawProperties.Directories directories;

    private FileStorageService fileStorageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        when(lawProperties.getDirectories()).thenReturn(directories);
        when(directories.getData()).thenReturn(tempDir.toString());
        when(directories.getPdfs()).thenReturn("pdfs");
        when(directories.getOcr()).thenReturn("ocr");
        when(directories.getArticles()).thenReturn("articles");

        fileStorageService = new FileStorageService(lawProperties);
    }

    @Test
    void givenTypeAndDocumentId_whenPdfPath_thenReturnsCorrectPdfPath() {
        // Given
        String type = "loi";
        String documentId = "loi-2024-15";

        // When
        Path result = fileStorageService.pdfPath(type, documentId);

        // Then
        assertThat(result).hasFileName("loi-2024-15.pdf");
        assertThat(result.getParent()).hasFileName("loi");
        assertThat(result.toString()).contains("pdfs");
    }

    @Test
    void givenTypeAndDocumentId_whenOcrPath_thenReturnsCorrectOcrPath() {
        // Given
        String type = "decret";
        String documentId = "decret-2024-100";

        // When
        Path result = fileStorageService.ocrPath(type, documentId);

        // Then
        assertThat(result).hasFileName("decret-2024-100.txt");
        assertThat(result.getParent()).hasFileName("decret");
        assertThat(result.toString()).contains("ocr");
    }

    @Test
    void givenTypeAndDocumentId_whenJsonPath_thenReturnsCorrectJsonPath() {
        // Given
        String type = "loi";
        String documentId = "loi-2025-1";

        // When
        Path result = fileStorageService.jsonPath(type, documentId);

        // Then
        assertThat(result).hasFileName("loi-2025-1.json");
        assertThat(result.getParent()).hasFileName("loi");
        assertThat(result.toString()).contains("articles");
    }

    @Test
    void givenExistingPdfFile_whenPdfExists_thenReturnsTrue() throws IOException {
        // Given
        String type = "loi";
        String documentId = "loi-2024-15";
        Path pdfPath = fileStorageService.pdfPath(type, documentId);
        Files.createDirectories(pdfPath.getParent());
        Files.createFile(pdfPath);

        // When
        boolean exists = fileStorageService.pdfExists(type, documentId);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void givenNonExistingPdfFile_whenPdfExists_thenReturnsFalse() {
        // Given
        String type = "loi";
        String documentId = "loi-9999-999";

        // When
        boolean exists = fileStorageService.pdfExists(type, documentId);

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void givenExistingOcrFile_whenOcrExists_thenReturnsTrue() throws IOException {
        // Given
        String type = "decret";
        String documentId = "decret-2024-100";
        Path ocrPath = fileStorageService.ocrPath(type, documentId);
        Files.createDirectories(ocrPath.getParent());
        Files.createFile(ocrPath);

        // When
        boolean exists = fileStorageService.ocrExists(type, documentId);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void givenNonExistingOcrFile_whenOcrExists_thenReturnsFalse() {
        // Given
        String type = "decret";
        String documentId = "decret-9999-999";

        // When
        boolean exists = fileStorageService.ocrExists(type, documentId);

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void givenExistingJsonFile_whenJsonExists_thenReturnsTrue() throws IOException {
        // Given
        String type = "loi";
        String documentId = "loi-2024-15";
        Path jsonPath = fileStorageService.jsonPath(type, documentId);
        Files.createDirectories(jsonPath.getParent());
        Files.createFile(jsonPath);

        // When
        boolean exists = fileStorageService.jsonExists(type, documentId);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void givenNonExistingJsonFile_whenJsonExists_thenReturnsFalse() {
        // Given
        String type = "loi";
        String documentId = "loi-9999-999";

        // When
        boolean exists = fileStorageService.jsonExists(type, documentId);

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void givenInvalidType_whenPdfPath_thenThrowsIllegalArgumentException() {
        // Given
        String invalidType = "invalid";
        String documentId = "loi-2024-15";

        // When / Then
        assertThatThrownBy(() -> fileStorageService.pdfPath(invalidType, documentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid document type");
    }

    @Test
    void givenNullDocumentId_whenPdfPath_thenThrowsIllegalArgumentException() {
        // Given
        String type = "loi";
        String documentId = null;

        // When / Then
        assertThatThrownBy(() -> fileStorageService.pdfPath(type, documentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Document ID cannot be null or empty");
    }

    @Test
    void givenEmptyDocumentId_whenPdfPath_thenThrowsIllegalArgumentException() {
        // Given
        String type = "loi";
        String documentId = "";

        // When / Then
        assertThatThrownBy(() -> fileStorageService.pdfPath(type, documentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Document ID cannot be null or empty");
    }

    @Test
    void givenPathTraversalDocumentId_whenPdfPath_thenThrowsSecurityException() {
        // Given
        String type = "loi";
        String documentId = "../etc/passwd";

        // When / Then
        assertThatThrownBy(() -> fileStorageService.pdfPath(type, documentId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid document ID");
    }

    @Test
    void givenNonExistingDirectory_whenEnsureDirectoryExists_thenCreatesDirectory() throws IOException {
        // Given
        String type = "loi";
        String documentId = "loi-2024-15";
        Path pdfPath = fileStorageService.pdfPath(type, documentId);

        // When
        fileStorageService.ensureDirectoryExists(pdfPath.getParent());

        // Then
        assertThat(Files.exists(pdfPath.getParent())).isTrue();
        assertThat(Files.isDirectory(pdfPath.getParent())).isTrue();
    }

    @Test
    void givenExistingDirectory_whenEnsureDirectoryExists_thenDoesNotThrowException() throws IOException {
        // Given
        Path existingDir = tempDir.resolve("existing");
        Files.createDirectories(existingDir);

        // When / Then - Should not throw exception
        fileStorageService.ensureDirectoryExists(existingDir);

        assertThat(Files.exists(existingDir)).isTrue();
        assertThat(Files.isDirectory(existingDir)).isTrue();
    }
}
