package io.github.baokhang83.blastradius.gradle;

import io.github.baokhang83.blastradius.core.index.FileIndexStore;
import io.github.baokhang83.blastradius.core.index.IndexStore;
import io.github.baokhang83.blastradius.s3.S3IndexStoreConfiguration;
import io.github.baokhang83.blastradius.s3.S3IndexStoreFactory;
import java.io.File;
import java.net.URI;

record ConfiguredIndexStore(String type, String bucket, String prefix, String region, String endpoint) {
    IndexStore<DependencyIndex> create(File root) {
        if (type == null || type.isBlank() || "file".equalsIgnoreCase(type)) {
            return new FileIndexStore<>(root.toPath(), DependencyIndex.class);
        }
        if (!"s3".equalsIgnoreCase(type)) {
            throw new IllegalArgumentException("blastradius.indexStore must be file or s3");
        }
        return S3IndexStoreFactory.create(new S3IndexStoreConfiguration(
                bucket, prefix, region, endpoint == null || endpoint.isBlank() ? null : URI.create(endpoint)), DependencyIndex.class);
    }

    static void close(IndexStore<?> store) {
        if (store instanceof AutoCloseable closeable) {
            try { closeable.close(); } catch (Exception ignored) { }
        }
    }
}
