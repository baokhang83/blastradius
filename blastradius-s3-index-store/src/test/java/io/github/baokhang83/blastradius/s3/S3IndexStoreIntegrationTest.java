package io.github.baokhang83.blastradius.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

class S3IndexStoreIntegrationTest {

    private static final String BUCKET = "blastradius-indexes";

    @Test
    void trackRunnerStoreCanBeReadByAFreshSelectRunnerStore() throws IOException {
        try (S3ProtocolServer server = new S3ProtocolServer(); S3Client trackClient = client(server.endpoint());
                S3Client selectClient = client(server.endpoint())) {
            trackClient.createBucket(request -> request.bucket(BUCKET));
            S3IndexStore<StoredIndex> trackStore = new S3IndexStore<>(new AwsS3ObjectStore(trackClient, BUCKET), "ci", StoredIndex.class);
            S3IndexStore<StoredIndex> selectStore = new S3IndexStore<>(new AwsS3ObjectStore(selectClient, BUCKET), "ci", StoredIndex.class);

            trackStore.put(".blastradius/0123456789abcdef0123456789abcdef01234567/index.json", new StoredIndex("TRACK"));

            assertEquals(new StoredIndex("TRACK"), selectStore.get(".blastradius/0123456789abcdef0123456789abcdef01234567/index.json").orElseThrow());
        }
    }

    private static S3Client client(URI endpoint) {
        return S3Client.builder().endpointOverride(endpoint).region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .serviceConfiguration(S3Configuration.builder().chunkedEncodingEnabled(false).build())
                .forcePathStyle(true).build();
    }

    private static final class S3ProtocolServer implements AutoCloseable {
        private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
        private final HttpServer server;

        S3ProtocolServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String key = exchange.getRequestURI().getPath();
                if ("PUT".equals(exchange.getRequestMethod())) {
                    objects.put(key, exchange.getRequestBody().readAllBytes());
                    exchange.sendResponseHeaders(200, -1);
                } else if ("GET".equals(exchange.getRequestMethod()) && objects.containsKey(key)) {
                    byte[] value = objects.get(key);
                    exchange.sendResponseHeaders(200, value.length);
                    exchange.getResponseBody().write(value);
                } else {
                    byte[] error = "<Error><Code>NoSuchKey</Code></Error>".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(404, error.length);
                    exchange.getResponseBody().write(error);
                }
                exchange.close();
            });
            server.start();
        }

        URI endpoint() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private record StoredIndex(String mode) {}
}
