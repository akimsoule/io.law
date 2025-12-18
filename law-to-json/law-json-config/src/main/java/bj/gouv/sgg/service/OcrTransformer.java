package bj.gouv.sgg.service;

import bj.gouv.sgg.exception.IAException;
import bj.gouv.sgg.model.JsonResult;
import bj.gouv.sgg.entity.LawDocumentEntity;

import java.nio.file.Path;

/**
 * Interface pour la transformation OCR de base.
 * 
 * <p>Responsabilités :
 * <ul>
 *   <li>Extraction OCR depuis PDF (via Tesseract)</li>
 *   <li>Application des corrections CSV</li>
 *   <li>Extraction des articles via patterns regex</li>
 *   <li>Génération du JSON structuré</li>
 * </ul>
 * 
 * <p>Implémentation : Délègue à law-pdf-ocr et law-ocr-json
 */
public interface OcrTransformer {
    
    /**
     * Transforme un PDF en JSON via OCR + Corrections CSV.
     * 
     * @param document Document à transformer
     * @param pdfPath Chemin du fichier PDF
     * @return JsonResult avec JSON structuré et confiance OCR
     * @throws IAException Si la transformation échoue
     */
    JsonResult transform(LawDocumentEntity document, Path pdfPath) throws IAException;
}
