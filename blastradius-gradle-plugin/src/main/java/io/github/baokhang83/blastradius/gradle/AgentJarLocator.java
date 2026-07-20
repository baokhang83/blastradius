package io.github.baokhang83.blastradius.gradle;

import io.github.baokhang83.blastradius.core.tracking.DependencyTrackingAgent;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.GradleException;

/** Locates the self-contained core artifact that can be attached as a Java agent. */
final class AgentJarLocator {

    Path locate() {
        try {
            Path agentJar = Path.of(DependencyTrackingAgent.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            if (Files.isRegularFile(agentJar)) {
                return agentJar;
            }
        } catch (URISyntaxException | NullPointerException e) {
            throw new GradleException("could not resolve the blastradius tracking agent", e);
        }
        throw new GradleException("the blastradius tracking agent must be packaged as a JAR");
    }
}
