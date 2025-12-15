package bj.gouv.sgg.service;

import bj.gouv.sgg.model.DownloadResult;
import bj.gouv.sgg.repository.DownloadResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service dédié à la mise à jour des résultats de téléchargement en mode thread-safe.
 * Utilise REQUIRES_NEW pour isoler chaque transaction et éviter les deadlocks MySQL.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DownloadResultUpdateService {

    private final DownloadResultRepository downloadResultRepository;
    
    // Map de locks par documentId pour paralléliser les updates de documents différents
    private static final ConcurrentHashMap<String, Lock> DOCUMENT_LOCKS = new ConcurrentHashMap<>();

    /**
     * Obtient ou crée un lock pour un documentId spécifique.
     * Permet aux threads de travailler en parallèle sur des documents différents.
     */
    private Lock getLockForDocument(String documentId) {
        return DOCUMENT_LOCKS.computeIfAbsent(documentId, k -> new ReentrantLock());
    }

    /**
     * Sauvegarde ou met à jour un résultat de téléchargement de manière thread-safe.
     * 
     * @param documentId L'identifiant unique du document
     * @param url L'URL de téléchargement
     * @param pdfPath Le chemin du fichier PDF
     * @param sha256 Le hash SHA-256 du fichier
     * @param fileSize La taille du fichier en octets
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveDownloadResult(String documentId, String url, String pdfPath, 
                                    String sha256, Long fileSize) {
        Lock lock = getLockForDocument(documentId);
        lock.lock();
        try {
            DownloadResult downloadResult = downloadResultRepository.findByDocumentId(documentId)
                .orElse(DownloadResult.builder()
                    .documentId(documentId)
                    .build());
            
            // Mettre à jour les infos
            downloadResult.setUrl(url);
            downloadResult.setPdfPath(pdfPath);
            downloadResult.setSha256(sha256);
            downloadResult.setFileSize(fileSize);
            downloadResult.setDownloadedAt(LocalDateTime.now());
            
            downloadResultRepository.save(downloadResult);
            
            log.debug("✅ DownloadResult saved: {} ({} bytes)", documentId, fileSize);
        } finally {
            lock.unlock();
            // Nettoyer le lock si plus nécessaire (éviter memory leak)
            cleanupLock(documentId);
        }
    }

    /**
     * Nettoie le lock d'un document si personne ne l'attend.
     * Évite l'accumulation de locks en mémoire.
     */
    private void cleanupLock(String documentId) {
        Lock lock = DOCUMENT_LOCKS.get(documentId);
        if (lock != null && lock instanceof ReentrantLock) {
            ReentrantLock reentrantLock = (ReentrantLock) lock;
            // Si personne n'attend et pas verrouillé, on peut supprimer
            if (!reentrantLock.hasQueuedThreads() && !reentrantLock.isLocked()) {
                DOCUMENT_LOCKS.remove(documentId);
            }
        }
    }
    
    /**
     * Vérifie si un résultat de téléchargement existe déjà.
     */
    public boolean exists(String documentId) {
        return downloadResultRepository.existsByDocumentId(documentId);
    }
}
