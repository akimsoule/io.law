package bj.gouv.sgg.law.pdfimg;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class PdfToImagesConverterTest {

    @Test
    public void givenFixturePdfWhenConvertThenCreatesOneImage() throws Exception {
        // Given: a fixture PDF from test resources
        ClassPathResource resource = new ClassPathResource("pdf/loi-1961-20.pdf");
        Path tmpPdf = Files.createTempFile("test", ".pdf");
        Files.copy(resource.getInputStream(), tmpPdf, StandardCopyOption.REPLACE_EXISTING);

        // When: converting PDF to images
        Path tmpOut = Files.createTempDirectory("pdfimg-out");
        String docId = "test-doc-1";

        PdfToImagesConverter conv = new PdfToImagesConverter();
        int pages = conv.convertPdfToImages(tmpPdf, tmpOut, docId);

        // Then: one image is produced
        assertEquals(1, pages);
        Path docDir = tmpOut.resolve(docId);
        assertTrue(Files.exists(docDir.resolve("page-0001.png")));
    }

    @Test
    public void givenPdfWithAmpliationsWhenConvertThenStopsAfterDetection() throws Exception {
        // Given: a PDF with AMPLIATIONS on page 2
        Path tmpPdf = Files.createTempFile("test-ampli", ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage p1 = new PDPage();
            PDPage p2 = new PDPage();
            PDPage p3 = new PDPage();
            doc.addPage(p1);
            doc.addPage(p2);
            doc.addPage(p3);

            try (PDPageContentStream cs = new PDPageContentStream(doc, p2)) {
                cs.beginText();
                var font = org.apache.pdfbox.pdmodel.font.PDType0Font.load(doc, PDType1Font.class.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"));
                cs.setFont(font, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Page contenant AMPLIATIONS - stop");
                cs.endText();
            }
            doc.save(tmpPdf.toFile());
        }

        Path tmpOut = Files.createTempDirectory("pdfimg-out-ampli");
        String docId = "test-doc-ampli";

        PdfToImagesConverter conv = new PdfToImagesConverter();
        int pages = conv.convertPdfToImages(tmpPdf, tmpOut, docId);

        // Should stop at detection on page 2 -> only page 1 converted
        assertEquals(1, pages);
        Path docDir = tmpOut.resolve(docId);
        assertTrue(Files.exists(docDir.resolve("page-0001.png")));
        assertFalse(Files.exists(docDir.resolve("page-0002.png")));
    }
}
