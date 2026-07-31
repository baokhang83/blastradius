package io.github.baokhang83.blastradius.core.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class AgentJarIsolationTest {

    @Test
    void agentUsesTheTargetJunitPlatformInsteadOfBundlingOne() throws Exception {
        Path agentJar = findOwnAgentJar();

        try (JarFile jar = new JarFile(agentJar.toFile())) {
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("org/junit/")),
                    "the agent must not shadow a target project's JUnit Platform");
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("org/opentest4j/")),
                    "OpenTest4J belongs to the target test runtime too");
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("org/apiguardian/")),
                    "API Guardian belongs to the target test runtime too");
        }

        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-javaagent:" + agentJar,
                "-version")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("standalone agent JVM timed out");
        }
        assertEquals(0, process.exitValue(), () -> "agent premain must not require JUnit:\n" + output);
    }

    private static Path findOwnAgentJar() throws IOException {
        try (var files = Files.list(Path.of("target"))) {
            return files
                    .filter(path -> path.getFileName().toString().matches("blastradius-core-.*-agent\\.jar"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("agent jar not found in target/"));
        }
    }
}
