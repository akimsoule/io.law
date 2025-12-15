package bj.gouv.sgg.storage;

import bj.gouv.sgg.util.GsonProvider;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stockage JSON simple pour persistance sans base de données.
 * Thread-safe avec synchronisation sur fichier.
 */
public class JsonStorage<T> {
    
    private final Path storageFile;
    private final Gson gson;
    private final TypeToken<List<T>> typeToken;
    
    public JsonStorage(Path storageFile, TypeToken<List<T>> typeToken) {
        this.storageFile = storageFile;
        this.gson = GsonProvider.get();
        this.typeToken = typeToken;
        
        try {
            Files.createDirectories(storageFile.getParent());
            if (!Files.exists(storageFile)) {
                Files.writeString(storageFile, "[]");
            }
        } catch (IOException e) {
            throw new StorageException("Failed to initialize storage: " + storageFile, e);
        }
    }
    
    public synchronized List<T> readAll() {
        try {
            String json = Files.readString(storageFile);
            List<T> items = gson.fromJson(json, typeToken.getType());
            return items != null ? items : new ArrayList<>();
        } catch (IOException e) {
            throw new StorageException("Failed to read from: " + storageFile, e);
        }
    }
    
    public synchronized void writeAll(List<T> items) {
        try {
            String json = gson.toJson(items);
            Files.writeString(storageFile, json, 
                StandardOpenOption.CREATE, 
                StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Failed to write to: " + storageFile, e);
        }
    }
    
    public synchronized void append(T item) {
        List<T> items = readAll();
        items.add(item);
        writeAll(items);
    }
    
    public synchronized void appendAll(List<T> newItems) {
        List<T> items = readAll();
        items.addAll(newItems);
        writeAll(items);
    }
    
    public synchronized Optional<T> findFirst(java.util.function.Predicate<T> predicate) {
        return readAll().stream()
            .filter(predicate)
            .findFirst();
    }
    
    public synchronized List<T> findAll(java.util.function.Predicate<T> predicate) {
        return readAll().stream()
            .filter(predicate)
            .toList();
    }
    
    public synchronized void update(java.util.function.Predicate<T> predicate, 
                                   java.util.function.UnaryOperator<T> updater) {
        List<T> items = readAll();
        boolean modified = false;
        for (int i = 0; i < items.size(); i++) {
            if (predicate.test(items.get(i))) {
                items.set(i, updater.apply(items.get(i)));
                modified = true;
            }
        }
        if (modified) {
            writeAll(items);
        }
    }
    
    public synchronized void deleteIf(java.util.function.Predicate<T> predicate) {
        List<T> items = readAll();
        boolean modified = items.removeIf(predicate);
        if (modified) {
            writeAll(items);
        }
    }
    
    public synchronized long count() {
        return readAll().size();
    }
    
    public synchronized long count(java.util.function.Predicate<T> predicate) {
        return readAll().stream()
            .filter(predicate)
            .count();
    }
    
    public Path getStorageFile() {
        return storageFile;
    }
}
