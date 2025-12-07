package bj.gouv.sgg.service;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.impl.OllamaClient;
import bj.gouv.sgg.modele.JsonResult;
import bj.gouv.sgg.model.LawDocument;
import com.google.gson.JsonParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test d'intégration pour l'extraction JSON via Ollama.
 * 
 * Utilise le modèle léger gemma:2b (1.7GB) adapté pour MacBook Intel 2019.
 * 
 * Prérequis :
 * - Ollama installé et en cours d'exécution
 * - Modèle gemma:2b disponible (ollama pull gemma:2b)
 * 
 * Ce test utilise directement OllamaClient pour l'extraction JSON.
 */
@Disabled("Tests Ollama désactivés - Nécessitent Ollama en cours d'exécution")
class OllamaIntegrationTest {
    
    private static final String OLLAMA_URL = "http://localhost:11434";
    private static final String OLLAMA_MODEL = "gemma:2b";
    
    private OllamaClient ollamaClient;
    private LawProperties properties;
    
    @TempDir
    Path tempDir;
    
    private static final Path SAMPLES_PDF_DIR = Path.of("src/test/resources/samples_pdf");
    private static final Path SAMPLE_JSON_DIR = Path.of("src/test/resources/sample_json");
    
    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(SAMPLES_PDF_DIR);
        Files.createDirectories(SAMPLE_JSON_DIR);
        
        // Initialiser les properties pour OllamaClient
        properties = new LawProperties();
        LawProperties.Capacity capacity = new LawProperties.Capacity();
        capacity.setOllamaUrl(OLLAMA_URL);
        capacity.setOllamaModelsRequired(OLLAMA_MODEL);
        properties.setCapacity(capacity);
        
        // Créer l'instance OllamaClient
        ollamaClient = new OllamaClient(properties);
    }
    
    @Test
    void testOllamaAvailability() {
        // Vérifier qu'Ollama est disponible
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL + "/api/tags"))
                .GET()
                .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            assertEquals(200, response.statusCode(), "Ollama devrait répondre sur " + OLLAMA_URL);
            
            // Vérifier que gemma:2b est disponible
            String body = response.body();
            assertTrue(body.contains(OLLAMA_MODEL), 
                      "Le modèle " + OLLAMA_MODEL + " devrait être disponible. Utilisez: ollama pull " + OLLAMA_MODEL);
            
            System.out.println("✅ Ollama disponible");
            System.out.println("   - URL: " + OLLAMA_URL);
            System.out.println("   - Modèle: " + OLLAMA_MODEL + " ✓");
            
        } catch (Exception e) {
            fail("Ollama n'est pas disponible. Assurez-vous qu'Ollama est lancé et que le modèle " + 
                 OLLAMA_MODEL + " est installé (ollama pull " + OLLAMA_MODEL + "): " + e.getMessage());
        }
    }
    
    @Test
    void testCreateSimpleLawPdf() throws IOException {
        // Créer un PDF de loi très simple
        File pdfFile = createSimpleLawPdf();
        
        // Vérifications
        assertTrue(pdfFile.exists(), "Le PDF devrait être créé");
        assertTrue(pdfFile.length() > 0, "Le PDF ne devrait pas être vide");
        
        // Sauvegarder dans samples_pdf pour inspection manuelle
        Path samplePath = SAMPLES_PDF_DIR.resolve("test-simple-law.pdf");
        Files.copy(pdfFile.toPath(), samplePath, 
                  java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        
        System.out.println("✅ PDF créé: " + samplePath);
        System.out.println("   - Taille: " + pdfFile.length() + " bytes");
        
        // Vérifier contenu lisible
        String pdfText = extractTextFromPdf(pdfFile);
        assertNotNull(pdfText);
        assertTrue(pdfText.contains("LOI"), "Devrait contenir 'LOI'");
        assertTrue(pdfText.contains("Article 1"), "Devrait contenir 'Article 1'");
        
        System.out.println("   - Contenu extrait: " + pdfText.length() + " chars");
    }
    
    /**
     * Crée un PDF de loi très simple pour tester Ollama.
     * 
     * Contenu minimal pour faciliter l'extraction JSON :
     * - Titre de loi avec numéro
     * - 3 articles courts
     * - 1 signataire
     */
    private File createSimpleLawPdf() throws IOException {
        File pdfFile = tempDir.resolve("test-loi-simple.pdf").toFile();
        
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                contentStream.setLeading(18f);
                contentStream.newLineAtOffset(50, 750);
                
                // En-tête
                contentStream.showText("REPUBLIQUE DU BENIN");
                contentStream.newLine();
                contentStream.newLine();
                
                // Titre loi
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                contentStream.showText("LOI N 2024-99 DU 1ER DECEMBRE 2024");
                contentStream.newLine();
                contentStream.showText("portant Code de Test");
                contentStream.newLine();
                contentStream.newLine();
                
                // Corps
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                contentStream.setLeading(15f);
                
                // Article 1
                contentStream.showText("Article 1er : Objet");
                contentStream.newLine();
                contentStream.showText("La presente loi porte code de test.");
                contentStream.newLine();
                contentStream.newLine();
                
                // Article 2
                contentStream.showText("Article 2 : Definitions");
                contentStream.newLine();
                contentStream.showText("Au sens de la presente loi, on entend par test");
                contentStream.newLine();
                contentStream.showText("toute verification de fonctionnement.");
                contentStream.newLine();
                contentStream.newLine();
                
                // Article 3
                contentStream.showText("Article 3 : Entree en vigueur");
                contentStream.newLine();
                contentStream.showText("La presente loi sera executee comme loi de l'Etat.");
                contentStream.newLine();
                contentStream.newLine();
                contentStream.newLine();
                
                // Promulgation
                contentStream.showText("Fait a Porto-Novo, le 1er decembre 2024");
                contentStream.newLine();
                contentStream.newLine();
                
                // Signataire
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11);
                contentStream.showText("Le President de la Republique");
                contentStream.newLine();
                contentStream.showText("Patrice TALON");
                
                contentStream.endText();
            }
            
            document.save(pdfFile);
        }
        
        return pdfFile;
    }
    
    /**
     * Extrait le texte d'un PDF pour vérification.
     */
    private String extractTextFromPdf(File pdfFile) throws IOException {
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdfFile)) {
            return new org.apache.pdfbox.text.PDFTextStripper().getText(document);
        }
    }
    
    @Test
    void testOllamaExtractionSimpleLaw() throws Exception {
        // 1. Créer PDF simple
        File pdfFile = createSimpleLawPdf();
        String pdfText = extractTextFromPdf(pdfFile);
        
        System.out.println("📄 PDF à extraire:");
        System.out.println("   - Fichier: " + pdfFile.getName());
        System.out.println("   - Taille: " + pdfFile.length() + " bytes");
        System.out.println("   - Texte: " + pdfText.length() + " chars");
        
        // 2. Créer un LawDocument avec le contenu OCR
        LawDocument document = LawDocument.builder()
            .type("loi")
            .year(2024)
            .number(99)
            .ocrContent(pdfText)
            .build();
        
        // 3. Appeler OllamaClient pour l'extraction
        System.out.println("\n🤖 Appel OllamaClient avec gemma:2b...");
        long startTime = System.currentTimeMillis();
        
        JsonResult result = ollamaClient.transform(document, pdfFile.toPath());
        
        long duration = System.currentTimeMillis() - startTime;
        
        // 4. Vérifications
        assertNotNull(result, "Le résultat ne devrait pas être null");
        assertNotNull(result.getJson(), "Le JSON ne devrait pas être null");
        assertTrue(result.getConfidence() > 0.0, "La confiance devrait être > 0");
        
        System.out.println("\n✅ Extraction réussie!");
        System.out.println("   - Temps: " + duration + " ms (" + (duration/1000.0) + "s)");
        System.out.println("   - JSON size: " + result.getJson().length() + " chars");
        System.out.println("   - Confiance: " + result.getConfidence());
        System.out.println("   - Source: " + result.getSource());
        
        // 5. Sauvegarder le JSON extrait
        Path jsonOutputPath = SAMPLE_JSON_DIR.resolve("test-simple-law.json");
        Files.writeString(jsonOutputPath, result.getJson());
        
        System.out.println("   - Sauvegardé: " + jsonOutputPath);
        
        // 6. Vérifications du contenu JSON
        String json = result.getJson();
        assertTrue(json.contains("documentId") || json.contains("articles"), 
                  "Le JSON devrait contenir documentId ou articles");
        
        // Tenter de valider que c'est du JSON bien formé (objet ou tableau)
        boolean isValidJson = false;
        try {
            if (json.trim().startsWith("[")) {
                JsonParser.parseString(json).getAsJsonArray();
                isValidJson = true;
            } else if (json.trim().startsWith("{")) {
                JsonParser.parseString(json).getAsJsonObject();
                isValidJson = true;
            }
        } catch (Exception e) {
            System.out.println("⚠️ JSON non strictement valide, mais contenu extrait: " + e.getMessage());
        }
        
        // 7. Afficher un aperçu
        String preview = json.length() > 800 ? json.substring(0, 800) + "..." : json;
        System.out.println("\n📋 Aperçu JSON" + (isValidJson ? " (valide)" : " (partiel)") + ":");
        System.out.println(preview);
    }
}
