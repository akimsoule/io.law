package bj.gouv.sgg.service;

import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.exception.PromptLoadException;
import bj.gouv.sgg.modele.JsonResult;
import bj.gouv.sgg.model.LawDocument;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour l'interface IAService
 */
class IAServiceTest {

    @Test
    void givenIAServiceInterfaceWhenCheckInheritanceThenExtendsRequiredInterfaces() {
        // Test que l'interface IAService hérite bien de ExtractToJson et LoadPrompt
        assertTrue(ExtractToJson.class.isAssignableFrom(IAService.class));
        assertTrue(LoadPrompt.class.isAssignableFrom(IAService.class));
    }

    @Test
    void givenMockImplementationWhenTransformAndLoadPromptThenReturnsExpectedResults() throws IAException, PromptLoadException {
        IAService mockService = new IAService() {
            @Override
            public String getSourceName() {
                return "MOCK";
            }
            
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String generateTextWithImages(String prompt, String systemPrompt, java.util.List<String> imagesBase64) throws IAException {
                return "{\"documentId\":\"test\",\"articles\":[]}";
            }

            @Override
            public JsonResult transform(LawDocument document, Path pdfPath) throws IAException {
                return new JsonResult(
                    "{\"documentId\":\"" + document.getDocumentId() + "\"}",
                    0.8,
                    "MOCK"
                );
            }

            @Override
            public String loadPrompt(String filename) throws PromptLoadException {
                return "Mock prompt for " + filename;
            }
        };

        // Test transform
        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(15)
            .build();
        
        JsonResult result = mockService.transform(document, Paths.get("test.pdf"));
        assertNotNull(result);
        assertEquals(0.8, result.getConfidence());
        assertEquals("MOCK", result.getSource());
        assertTrue(result.getJson().contains("loi-2024-15"));

        // Test loadPrompt
        String prompt = mockService.loadPrompt("test.txt");
        assertNotNull(prompt);
        assertTrue(prompt.contains("test.txt"));
    }
}
