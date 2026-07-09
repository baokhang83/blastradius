package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.fail;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import io.github.baokhang83.blastradius.core.tracking.DependencyRecordReader;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex.TestDependencyEntry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Shared machinery for {@code blastradius-maven-plugin}'s real, subprocess-driven
 * end-to-end tests ({@link SelectModeEndToEndTest}, {@link SelectModeMultiModuleEndToEndTest},
 * and US2's live-trust tests) — installing the plugin once, tracking a fixture project the
 * same way {@code TrackRunner} does, and running/asserting on a real {@code mvn test}
 * subprocess. Test-support code, not engine code (mirrors {@code FixtureProjectBuilder}'s
 * own note — not held to the constitution's TDD-first rule).
 */
final class EndToEndTestSupport {

    private EndToEndTestSupport() {
    }

    static void installThisPluginOnce() throws IOException, InterruptedException {
        runToCompletion(new ProcessBuilder("mvn", "-q", "install", "-DskipTests")
                .directory(Path.of(".").toAbsolutePath().toFile()));
    }

    /** A single-module fixture with an independent Foo/FooTest and Bar/BarTest pair. */
    static FixtureProjectBuilder seedFooBarFixture(Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.ignoreTargetDirectory();
        fixture.writeClass("com.example.Foo",
                "package com.example; public class Foo { public int value() { return 1; } }");
        fixture.writeTest("com.example.FooTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class FooTest {
                    @Test
                    void checksFoo() { assertEquals(1, new Foo().value()); }
                }
                """);
        fixture.writeClass("com.example.Bar",
                "package com.example; public class Bar { public int value() { return 2; } }");
        fixture.writeTest("com.example.BarTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class BarTest {
                    @Test
                    void checksBar() { assertEquals(2, new Bar().value()); }
                }
                """);
        return fixture;
    }

    static String pluginXml(String anchorCommit) {
        return """
                        <plugin>
                            <groupId>io.github.baokhang83.blastradius</groupId>
                            <artifactId>blastradius-maven-plugin</artifactId>
                            <version>0.1.0-SNAPSHOT</version>
                            <executions>
                                <execution>
                                    <phase>process-test-classes</phase>
                                    <goals><goal>select</goal></goals>
                                </execution>
                            </executions>
                            <configuration>
                                <baseRef>%s</baseRef>
                            </configuration>
                        </plugin>
                """.formatted(anchorCommit);
    }

    static DependencyIndex trackDependencies(Path projectDir, String anchorCommit)
            throws IOException, InterruptedException {
        Path agentJar = findCoreAgentJar();
        Path outputFile = Files.createTempFile("blastradius-e2e-track-", ".json");
        String agentOption = "-javaagent:" + agentJar.toAbsolutePath() + "=" + outputFile.toAbsolutePath();
        ProcessBuilder pb = new ProcessBuilder("mvn", "-B", "--no-transfer-progress", "clean", "test")
                .directory(projectDir.toFile());
        pb.environment().merge("JAVA_TOOL_OPTIONS", agentOption, (a, b) -> a + " " + b);
        runToCompletion(pb);

        Map<TestIdentity, Map<String, String>> recorded = new DependencyRecordReader().readAll(outputFile);
        List<TestDependencyEntry> entries = recorded.entrySet().stream()
                .map(e -> new TestDependencyEntry(e.getKey(), e.getValue().keySet()))
                .toList();
        return new DependencyIndex(anchorCommit, Instant.now().toString(), entries);
    }

    static String runMvnTest(Path projectDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("mvn", "-B", "--no-transfer-progress", "clean", "test")
                .directory(projectDir.toFile())
                .redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(3, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            fail("mvn test timed out against fixture project at " + projectDir);
        }
        return output;
    }

    static void runToCompletion(ProcessBuilder pb) throws IOException, InterruptedException {
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            fail("subprocess timed out: " + String.join(" ", pb.command()));
        }
        if (process.exitValue() != 0) {
            fail("subprocess failed (exit " + process.exitValue() + "): " + String.join(" ", pb.command()) + "\n" + output);
        }
    }

    static Path findCoreAgentJar() throws IOException {
        Path targetDir = Path.of("..", "blastradius-core", "target");
        try (var stream = Files.list(targetDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().matches("blastradius-core-.*\\.jar"))
                    .filter(p -> !p.getFileName().toString().contains("sources"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "blastradius-core agent jar not found in ../blastradius-core/target"));
        }
    }
}
