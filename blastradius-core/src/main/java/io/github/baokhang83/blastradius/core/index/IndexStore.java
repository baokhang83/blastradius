package io.github.baokhang83.blastradius.core.index;

import java.util.Optional;

/**
 * Stores dependency indexes under caller-defined, stable keys.
 *
 * <p>The key is opaque to callers: the local implementation maps it below its configured root,
 * while a future remote implementation can use the same key as an object name.
 *
 * @param <T> the index value type
 */
public interface IndexStore<T> {

    /** Returns the value for {@code key}, or empty when no value has been stored for it. */
    Optional<T> get(String key);

    /** Stores {@code value} under {@code key}, replacing a value previously stored for that key. */
    void put(String key, T value);
}
