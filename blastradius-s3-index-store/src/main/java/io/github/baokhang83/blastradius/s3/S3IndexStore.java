package io.github.baokhang83.blastradius.s3;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.baokhang83.blastradius.core.index.IndexStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.List;

/** A JSON-backed {@link IndexStore} whose values live under an S3 object-key prefix. */
public final class S3IndexStore<T> implements IndexStore<T>, AutoCloseable {

    private final S3ObjectStore objects;
    private final String prefix;
    private final Class<T> valueType;
    private final ObjectMapper mapper = new ObjectMapper();

    S3IndexStore(S3ObjectStore objects, String prefix, Class<T> valueType) {
        this.objects = objects;
        this.prefix = normalizePrefix(prefix);
        this.valueType = valueType;
    }

    @Override
    public Optional<T> get(String key) {
        return objects.get(objectKey(key)).map(this::deserialize);
    }

    @Override
    public List<String> keys(String prefix) {
        String objectPrefix = objectPrefix(prefix);
        return objects.keys(objectPrefix).stream().map(this::relativeKey).toList();
    }

    @Override
    public void put(String key, T value) {
        try {
            objects.put(objectKey(key), mapper.writeValueAsBytes(value));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to serialize dependency index", e);
        }
    }

    @Override
    public void close() {
        objects.close();
    }

    private T deserialize(byte[] payload) {
        try {
            T value = mapper.readValue(payload, valueType);
            if (value == null) {
                throw new IOException("index value must not be null");
            }
            return value;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read dependency index from S3", e);
        }
    }

    private String objectKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("index key must not be blank");
        }
        // An S3 object key is a logical, always-forward-slash path, so absoluteness must be
        // judged OS-independently: a leading '/' (or '\') is "absolute" for our purposes even
        // though Path.of("/x").isAbsolute() is false on Windows, where an absolute path needs a
        // drive letter — relying on Path.isAbsolute() alone lets "/absolute/key" slip the guard
        // on Windows.
        if (key.startsWith("/") || key.startsWith("\\")) {
            throw new IllegalArgumentException("index key must stay below the configured S3 prefix: " + key);
        }
        try {
            Path path = Path.of(key).normalize();
            if (path.isAbsolute() || path.getNameCount() == 0 || path.startsWith("..")) {
                throw new IllegalArgumentException("index key must stay below the configured S3 prefix: " + key);
            }
            String normalizedKey = path.toString().replace('\\', '/');
            return prefix.isEmpty() ? normalizedKey : prefix + "/" + normalizedKey;
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("invalid index key: " + key, e);
        }
    }

    private String objectPrefix(String relativePrefix) {
        if (relativePrefix == null || relativePrefix.isBlank()) {
            return prefix;
        }
        return objectKey(relativePrefix);
    }

    private String relativeKey(String objectKey) {
        if (prefix.isEmpty()) {
            return objectKey;
        }
        String prefixWithSeparator = prefix + "/";
        if (!objectKey.startsWith(prefixWithSeparator)) {
            throw new IllegalArgumentException("S3 object key is outside the configured prefix: " + objectKey);
        }
        return objectKey.substring(prefixWithSeparator.length());
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        String normalized = prefix.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank() || normalized.equals(".") || normalized.startsWith("../") || normalized.contains("/../")) {
            throw new IllegalArgumentException("S3 prefix must not escape its bucket: " + prefix);
        }
        return normalized;
    }
}
