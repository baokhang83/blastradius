package io.github.baokhang83.blastradius.gradle;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class PublishedArtifactTest {

    @Test
    void shadowJarContainsPluginAgentAndS3Implementation() throws Exception {
        Path shadowJar;
        try (var jars = Files.list(Path.of("build/libs"))) {
            shadowJar = jars.filter(p -> p.getFileName().toString().matches("blastradius-gradle-plugin-[^-]+\\.jar"))
                    .findFirst().orElseThrow();
        }
        try (JarFile jar = new JarFile(shadowJar.toFile())) {
            assertNotNull(jar.getEntry("io/github/baokhang83/blastradius/gradle/BlastradiusPlugin.class"));
            assertNotNull(jar.getEntry(
                    "io/github/baokhang83/blastradius/core/tracking/DependencyTrackingAgent.class"));
            assertNotNull(jar.getEntry("io/github/baokhang83/blastradius/s3/S3IndexStoreFactory.class"));
            assertTrue(jar.stream().anyMatch(entry -> entry.getName().startsWith("software/amazon/awssdk/services/s3/")));
            assertTrue(jar.getManifest().getMainAttributes().getValue("Premain-Class")
                    .equals("io.github.baokhang83.blastradius.core.tracking.DependencyTrackingAgent"));
        }
    }
}
