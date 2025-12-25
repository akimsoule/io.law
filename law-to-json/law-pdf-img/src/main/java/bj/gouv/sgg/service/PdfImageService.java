package bj.gouv.sgg.service;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.util.FileExistenceHelper;
import bj.gouv.sgg.law.pdfimg.PdfToImagesConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
@RequiredArgsConstructor
public class PdfImageService {

    private final AppConfig config;
    private final PdfToImagesConverter converter = new PdfToImagesConverter();

    /**
     * Convertit le PDF (pdfPath) en images sous `<imagesDir>/<documentId>/page-XXXX.png`
     * Retourne le nombre de pages/Images générées.
     */
    public int convertPdfToImages(String documentId, Path pdfPath) throws IOException {
        Path imagesBase = config.getImagesDir();
        Files.createDirectories(imagesBase);
        return converter.convertPdfToImages(pdfPath, imagesBase, documentId);
    }

    public boolean imagesExist(String documentId) {
        Path p = config.getImagesDir().resolve(documentId);
        return FileExistenceHelper.exists(p, "PdfImageService.imagesExist");
    }
}
