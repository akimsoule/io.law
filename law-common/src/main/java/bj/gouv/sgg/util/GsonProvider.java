package bj.gouv.sgg.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Provider Gson configuré avec adaptateurs pour types Java modernes.
 */
public class GsonProvider {
    
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    
    private static final Gson INSTANCE = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .create();
    
    // Constructeur privé pour empêcher l'instanciation
    private GsonProvider() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    public static Gson get() {
        return INSTANCE;
    }
    
    /**
     * Adaptateur Gson pour LocalDateTime.
     */
    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(DATE_TIME_FORMATTER));
            }
        }
        
        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            String value = in.nextString();
            return value == null || value.isEmpty() ? null : LocalDateTime.parse(value, DATE_TIME_FORMATTER);
        }
    }
    
    /**
     * Adaptateur Gson pour LocalDate.
     */
    private static class LocalDateAdapter extends TypeAdapter<LocalDate> {
        @Override
        public void write(JsonWriter out, LocalDate value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(DATE_FORMATTER));
            }
        }
        
        @Override
        public LocalDate read(JsonReader in) throws IOException {
            String value = in.nextString();
            return value == null || value.isEmpty() ? null : LocalDate.parse(value, DATE_FORMATTER);
        }
    }
}
