package io.github.baokhang83.blastradius.s3;

import java.net.URI;

/** Non-secret configuration required to address an S3 or S3-compatible index store. */
public record S3IndexStoreConfiguration(String bucket, String prefix, String region, URI endpoint) {

    public S3IndexStoreConfiguration {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("S3 bucket must not be blank");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("S3 region must not be blank");
        }
    }
}
