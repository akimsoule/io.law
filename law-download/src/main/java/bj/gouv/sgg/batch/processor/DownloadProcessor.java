package bj.gouv.sgg.batch.processor;

import bj.gouv.sgg.model.LawDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Processor qui télécharge le PDF d'un document
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DownloadProcessor implements ItemProcessor<LawDocument, LawDocument> {
    
    private boolean forceMode = false;
    
    /**
     * Active le mode force (re-téléchargement même si déjà DOWNLOADED)
     */
    public void setForceMode(boolean force) {
        this.forceMode = force;
        log.debug("Force mode set: {}", force);
    }
    
    @Override
    public LawDocument process(LawDocument document) throws Exception {
        // Skip if already downloaded (idempotence via status), SAUF en mode force
        if (!forceMode && document.getStatus() == LawDocument.ProcessingStatus.DOWNLOADED) {
            log.debug("⏭️ PDF already downloaded, skipping: {}", document.getDocumentId());
            return null;
        }
        
        // En mode force, toujours re-télécharger
        if (forceMode && document.getStatus() == LawDocument.ProcessingStatus.DOWNLOADED) {
            log.info("🔄 Force re-download: {}", document.getDocumentId());
        }
        
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            // Ajouter /download à l'URL de base
            String downloadUrl = document.getUrl() + "/download";
            log.debug("Downloading from: {}", downloadUrl);
            
            HttpGet request = new HttpGet(downloadUrl);
            request.setHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");

            try (var response = client.executeOpen(null, request, null)) {
                if (response.getCode() == 200) {
                    try (InputStream is = response.getEntity().getContent();
                         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");

                        while ((bytesRead = is.read(buffer)) != -1) {
                            baos.write(buffer, 0, bytesRead);
                            digest.update(buffer, 0, bytesRead);
                        }

                        byte[] pdfBytes = baos.toByteArray();
                        byte[] hashBytes = digest.digest();
                        StringBuilder sb = new StringBuilder();
                        for (byte b : hashBytes) {
                            sb.append(String.format("%02x", b));
                        }

                        String sha256Hash = sb.toString();

                        document.setSha256(sha256Hash);
                        document.setPdfPath(document.getDocumentId()); // Virtual path
                        document.setStatus(LawDocument.ProcessingStatus.DOWNLOADED);
                        document.setPdfContent(pdfBytes); // Stocker le contenu pour le writer

                        log.debug("Downloaded: {} ({} bytes)",
                                document.getDocumentId(), pdfBytes.length);

                        return document;
                    }
                } else {
                    log.error("Download failed for {}: HTTP {}",
                            document.getDocumentId(), response.getCode());
                    return null;
                }
            }
        } catch (Exception e) {
            log.error("Error downloading {}: {}", document.getDocumentId(), e.getMessage());
            throw e;
        }
    }
}
