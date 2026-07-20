package io.github.baokhang83.blastradius.core.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** A JSON-backed {@link IndexStore} whose keys are constrained to one project directory. */
public final class FileIndexStore<T> implements IndexStore<T> {

    private final Path rootDirectory;
    private final Class<T> valueType;
    private final ObjectMapper mapper = new ObjectMapper();

    public FileIndexStore(Path rootDirectory, Class<T> valueType) {
        this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
        this.valueType = valueType;
    }

    @Override
    public Optional<T> get(String key) {
        Path indexFile = resolveKey(key);
        if (Files.notExists(indexFile)) {
            return Optional.empty();
        }
        try {
            T value = mapper.readValue(indexFile.toFile(), valueType);
            if (value == null) {
                throw new IOException("index value must not be null: " + indexFile);
            }
            return Optional.of(value);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read dependency index from " + indexFile, e);
        }
    }

    @Override
    public void put(String key, T value) {
        Path indexFile = resolveKey(key);
        try {
            Path parent = indexFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            mapper.writeValue(indexFile.toFile(), value);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write dependency index to " + indexFile, e);
        }
    }

    private Path resolveKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("index key must not be blank");
        }
        Path keyPath = Path.of(key);
        if (keyPath.isAbsolute()) {
            throw new IllegalArgumentException("index key must be relative to the configured root: " + key);
        }
        Path indexFile = rootDirectory.resolve(keyPath).normalize();
        if (!indexFile.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("index key resolves outside the configured root: " + key);
        }
        return indexFile;
    }
}
