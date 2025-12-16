package bj.gouv.sgg.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Configuration Gson pour la sérialisation/désérialisation JSON dans le module QA.
 * Factory simple pour créer instance Gson configurée.
 */
public class GsonConfiguration {
    
    /**
     * Crée instance Gson avec pretty-printing et sérialisation nulls.
     */
    public static Gson createGson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .create();
    }
}
