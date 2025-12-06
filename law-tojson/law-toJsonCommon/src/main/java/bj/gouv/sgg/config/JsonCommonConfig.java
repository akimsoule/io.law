package bj.gouv.sgg.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Configuration
public class JsonCommonConfig {

    /**
     * Bean Gson avec configuration standard pour la sérialisation JSON
     * - Pretty printing activé
     * - Exclusion des champs sans @Expose
     * - Support LocalDate et LocalDateTime
     */
    @Bean
    public Gson gson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .excludeFieldsWithoutExposeAnnotation()
                .registerTypeAdapter(LocalDate.class, (com.google.gson.JsonSerializer<LocalDate>) (src, typeOfSrc, context) ->
                        src == null ? null : new com.google.gson.JsonPrimitive(src.toString()))
                .registerTypeAdapter(LocalDate.class, (com.google.gson.JsonDeserializer<LocalDate>) (json, typeOfT, context) ->
                        json == null ? null : LocalDate.parse(json.getAsString()))
                .registerTypeAdapter(LocalDateTime.class, (com.google.gson.JsonSerializer<LocalDateTime>) (src, typeOfSrc, context) ->
                        src == null ? null : new com.google.gson.JsonPrimitive(src.toString()))
                .registerTypeAdapter(LocalDateTime.class, (com.google.gson.JsonDeserializer<LocalDateTime>) (json, typeOfT, context) ->
                        json == null ? null : LocalDateTime.parse(json.getAsString()))
                .create();
    }

    /**
     * Bean Gson simple pour la sérialisation basique (sans @Expose filtering)
     */
    @Bean(name = "simpleGson")
    public Gson simpleGson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }


}
