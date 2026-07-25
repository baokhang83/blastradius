package io.github.baokhang83.blastradius.s3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** AWS SDK adapter that treats a missing object as an absent dependency index. */
final class AwsS3ObjectStore implements S3ObjectStore {

    private final S3Client client;
    private final String bucket;

    AwsS3ObjectStore(S3Client client, String bucket) {
        this.client = client;
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("S3 bucket must not be blank");
        }
        this.bucket = bucket;
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            ResponseBytes<GetObjectResponse> response = client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(key).build(), ResponseTransformer.toBytes());
            return Optional.of(response.asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw unavailable("read", key, e);
        } catch (SdkException e) {
            throw unavailable("read", key, e);
        }
    }

    @Override
    public void put(String key, byte[] value) {
        try {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/json").build(),
                    RequestBody.fromBytes(value));
        } catch (SdkException e) {
            throw unavailable("write", key, e);
        }
    }

    @Override
    public void close() {
        client.close();
    }

    private static UncheckedIOException unavailable(String operation, String key, SdkException cause) {
        return new UncheckedIOException("failed to " + operation + " dependency index at S3 key " + key,
                new IOException(cause));
    }
}
