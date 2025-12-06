package bj.gouv.sgg.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utilitaire pour standardiser les vérifications d'existence de fichiers
 * avec logging cohérent à travers toute l'application
 */
@Slf4j
public class FileExistenceHelper {
    
    /**
     * Vérifie l'existence d'un fichier avec logging standardisé
     * 
     * @param path Path du fichier à vérifier
     * @param context Contexte de l'opération pour le logging
     * @return true si le fichier existe
     */
    public static boolean exists(Path path, String context) {
        boolean exists = Files.exists(path);
        if (!exists) {
            log.debug("[{}] File not found: {}", context, path);
        }
        return exists;
    }
    
    /**
     * Vérifie l'existence d'un fichier avec logging standardisé
     * 
     * @param file File à vérifier
     * @param context Contexte de l'opération pour le logging
     * @return true si le fichier existe
     */
    public static boolean exists(File file, String context) {
        return exists(file.toPath(), context);
    }
    
    /**
     * Vérifie existence + log warning si absent
     * 
     * @param path Path du fichier à vérifier
     * @param context Contexte de l'opération pour le logging
     * @return true si le fichier existe
     */
    public static boolean requireExists(Path path, String context) {
        boolean exists = Files.exists(path);
        if (!exists) {
            log.warn("[{}] Required file not found: {}", context, path);
        }
        return exists;
    }
    
    /**
     * Vérifie existence + log warning si absent
     * 
     * @param file File à vérifier
     * @param context Contexte de l'opération pour le logging
     * @return true si le fichier existe
     */
    public static boolean requireExists(File file, String context) {
        return requireExists(file.toPath(), context);
    }
    
    /**
     * Vérifie existence ou throw exception
     * 
     * @param path Path du fichier à vérifier
     * @param context Contexte de l'opération pour le logging
     * @throws FileNotFoundException si le fichier n'existe pas
     */
    public static void ensureExists(Path path, String context) throws FileNotFoundException {
        if (!Files.exists(path)) {
            throw new FileNotFoundException(
                String.format("[%s] File not found: %s", context, path)
            );
        }
    }
    
    /**
     * Vérifie existence ou throw exception
     * 
     * @param file File à vérifier
     * @param context Contexte de l'opération pour le logging
     * @throws FileNotFoundException si le fichier n'existe pas
     */
    public static void ensureExists(File file, String context) throws FileNotFoundException {
        ensureExists(file.toPath(), context);
    }
    
    // Prevent instantiation
    private FileExistenceHelper() {}
}
