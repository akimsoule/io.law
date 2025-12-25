package bj.gouv.sgg.law.pdfimg;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PdfToImagesConverter {
    private static final Logger LOG = LoggerFactory.getLogger(PdfToImagesConverter.class);

    /**
     * Convertit le PDF en images PNG, une image par page.
     * Les images sont écrites dans `outputBaseDir/documentId/` nommées page-0001.png, page-0002.png ...
     * Retourne le nombre de pages converties.
     */
    public int convertPdfToImages(Path pdfPath, Path outputBaseDir, String documentId) throws IOException {
        if (pdfPath == null || !Files.exists(pdfPath)) {
            throw new IllegalArgumentException("pdfPath doit exister: " + pdfPath);
        }
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId invalide");
        }
        Path destDir = outputBaseDir.resolve(documentId);
        Files.createDirectories(destDir);

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            PDFTextStripper stripper = new PDFTextStripper();
            int pageCount = document.getNumberOfPages();
            LOG.info("Conversion de {} pages pour {} -> {}", pageCount, pdfPath, destDir);
            boolean detected = false;

            for (int i = 0; i < pageCount; i++) {
                if (detected) {
                    LOG.info("Arrêt de la conversion d'images à la page {} suite à la détection d'AMPLIATIONS.", i + 1);
                    break;
                }
                // Inspecter le texte de la page avant de la convertir
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String pageText = stripper.getText(document);
                if (pageText != null && pageText.contains("AMPLIATIONS")) {
                    LOG.info("🚨 AMPLIATIONS détecté à la page {} - arrêt de la conversion d'images.", i + 1);
                    detected = true;
                    // On arrête la conversion pour les pages suivantes
                }

                BufferedImage bim = renderer.renderImageWithDPI(i, 300, ImageType.RGB);
                String fileName = String.format("page-%04d.png", i + 1);
                Path imagePath = destDir.resolve(fileName);
                ImageIO.write(bim, "png", imagePath.toFile());
                LOG.debug("Écrit image : {}", imagePath);
            }
            return pageCount;
        }
    }
}
