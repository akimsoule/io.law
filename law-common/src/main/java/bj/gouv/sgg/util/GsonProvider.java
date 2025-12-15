package bj.gouv.sgg.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Provider Gson configuré avec adaptateurs pour types Java modernes.
 */
public class GsonProvider {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    private static final Gson INSTANCE = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();
    
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
                out.value(value.format(FORMATTER));
            }
        }
        
        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            String value = in.nextString();
            return value == null || value.isEmpty() ? null : LocalDateTime.parse(value, FORMATTER);
        }
    }
}
