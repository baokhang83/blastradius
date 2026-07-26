package io.github.baokhang83.blastradius.s3;

import java.util.Optional;
import java.util.List;

/** Byte-oriented object operations needed by {@link S3IndexStore}. */
interface S3ObjectStore extends AutoCloseable {

    Optional<byte[]> get(String key);

    List<String> keys(String prefix);

    void put(String key, byte[] value);

    @Override
    default void close() {}
}
