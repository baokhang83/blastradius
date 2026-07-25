package io.github.baokhang83.blastradius.s3;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/** Creates stores that authenticate through the AWS SDK's standard credential-provider chain. */
public final class S3IndexStoreFactory {

    private S3IndexStoreFactory() {}

    public static <T> S3IndexStore<T> create(S3IndexStoreConfiguration configuration, Class<T> valueType) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(configuration.region()))
                .credentialsProvider(DefaultCredentialsProvider.create());
        if (configuration.endpoint() != null) {
            builder.endpointOverride(configuration.endpoint()).forcePathStyle(true);
        }
        return new S3IndexStore<>(new AwsS3ObjectStore(builder.build(), configuration.bucket()),
                configuration.prefix(), valueType);
    }
}
