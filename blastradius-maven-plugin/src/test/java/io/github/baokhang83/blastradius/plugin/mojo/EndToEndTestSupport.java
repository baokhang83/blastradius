package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.fail;

import io.github.baokhang83.blastradius.core.index.FileIndexStore;
import io.github.baokhang83.blastradius.core.index.CommitIndexKey;
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

    private static final PluginInstaller PLUGIN_INSTALLER = new PluginInstaller();

    private EndToEndTestSupport() {
    }

    static void installThisPluginOnce() throws IOException, InterruptedException {
        PLUGIN_INSTALLER.installOnce(() -> runToCompletion(new ProcessBuilder(
                "mvn", "-q", "-pl", "blastradius-maven-plugin", "-am", "install", "-DskipTests", "-Dinvoker.skip=true")
                .directory(Path.of("..").toAbsolutePath().normalize().toFile())));
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
                            <version>0.1.0</version>
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

    /** Plugin binding deliberately missing the required {@code baseRef} configuration (T049). */
    static String pluginXmlMissingBaseRef() {
        return """
                        <plugin>
                            <groupId>io.github.baokhang83.blastradius</groupId>
                            <artifactId>blastradius-maven-plugin</artifactId>
                            <version>0.1.0</version>
                            <executions>
                                <execution>
                                    <phase>process-test-classes</phase>
                                    <goals><goal>select</goal></goals>
                                </execution>
                            </executions>
                        </plugin>
                """;
    }

    /** Plugin binding with an explicit, possibly-invalid {@code indexPath} (T049). */
    static String pluginXml(String anchorCommit, String indexPath) {
        return """
                        <plugin>
                            <groupId>io.github.baokhang83.blastradius</groupId>
                            <artifactId>blastradius-maven-plugin</artifactId>
                            <version>0.1.0</version>
                            <executions>
                                <execution>
                                    <phase>process-test-classes</phase>
                                    <goals><goal>select</goal></goals>
                                </execution>
                            </executions>
                            <configuration>
                                <baseRef>%s</baseRef>
                                <indexPath>%s</indexPath>
                            </configuration>
                        </plugin>
                """.formatted(anchorCommit, indexPath);
    }

    /** Recursively deletes {@code projectDir}'s {@code .git} directory (T049: non-git target). */
    static void removeGitRepository(Path projectDir) throws IOException {
        Path gitDir = projectDir.resolve(".git");
        try (var stream = Files.walk(gitDir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
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

    static void writeIndex(Path projectDir, DependencyIndex index) {
        writeIndex(projectDir, index.anchorCommit(), index);
    }

    static void writeIndex(Path projectDir, String keyCommit, DependencyIndex index) {
        new FileIndexStore<>(projectDir, DependencyIndex.class).put(
                CommitIndexKey.forCommit(".blastradius/index.json", keyCommit), index);
    }

    static String runMvnTest(Path projectDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("mvn", "-B", "--no-transfer-progress", "clean", "test")
                .directory(projectDir.toFile());
        // 10m, not 3m: a TRACK-mode build forks its own nested `mvn clean test` in this
        // same directory (compile, then nested clean+compile+instrumented-test, then this
        // outer build's own test phase) — legitimately close to 3m by itself under load,
        // confirmed by observation, not a hang (see runAndCapture's javadoc for the
        // separate fix that makes this timeout actually enforceable).
        return runAndCapture(pb, 10, false);
    }

    static void runToCompletion(ProcessBuilder pb) throws IOException, InterruptedException {
        runAndCapture(pb, 5, true);
    }

    /**
     * Redirects the subprocess's output to a file and waits for it with {@code
     * waitFor(timeoutMinutes, ...)} before ever touching that file. Piping + reading
     * eagerly via {@code readAllBytes()} (as this class used to, and as {@code
     * MavenBuildRunner}/{@code DependencyTrackingIntegrationTest} still do elsewhere)
     * blocks until the child closes its stdout, which makes the timeout below unreachable
     * if a grandchild process — every one of these fixture builds runs {@code SelectMojo},
     * whose TRACK branch forks its own nested {@code mvn} subprocess — keeps that pipe's
     * write end open. On timeout, the whole descendant tree is destroyed, not just the
     * immediate child, so a hung nested {@code mvn}/JVM isn't left running (this is what
     * exhausted memory in a prior session: an orphaned grandchild survived the parent's
     * {@code destroyForcibly()} and kept running indefinitely).
     */
    private static String runAndCapture(ProcessBuilder pb, long timeoutMinutes, boolean failOnNonZero)
            throws IOException, InterruptedException {
        pb.redirectErrorStream(true);
        Path outputFile = Files.createTempFile("blastradius-e2e-subprocess-", ".log");
        pb.redirectOutput(ProcessBuilder.Redirect.to(outputFile.toFile()));
        try {
            Process process = pb.start();
            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            String output = Files.readString(outputFile, StandardCharsets.UTF_8);
            if (!finished) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                return fail("subprocess timed out after " + timeoutMinutes + "m: "
                        + String.join(" ", pb.command()) + "\n" + output);
            }
            if (failOnNonZero && process.exitValue() != 0) {
                return fail("subprocess failed (exit " + process.exitValue() + "): "
                        + String.join(" ", pb.command()) + "\n" + output);
            }
            return output;
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }

    static Path findCoreAgentJar() throws IOException {
        Path targetDir = Path.of("..", "blastradius-core", "target");
        try (var stream = Files.list(targetDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().matches("blastradius-core-.*\\.jar"))
                    .filter(p -> p.getFileName().toString().contains("-agent"))
                    .filter(p -> !p.getFileName().toString().contains("sources"))
                    .filter(p -> !p.getFileName().toString().contains("tests"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "blastradius-core agent jar not found in ../blastradius-core/target"));
        }
    }
}
