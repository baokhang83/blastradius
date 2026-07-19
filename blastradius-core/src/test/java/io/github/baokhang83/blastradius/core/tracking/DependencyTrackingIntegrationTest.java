package io.github.baokhang83.blastradius.core.tracking;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end: attach {@link DependencyTrackingAgent} + {@link TestBoundaryListener} to a
 * real {@code mvn test} subprocess run against a {@link FixtureProjectBuilder} project,
 * for both a single-module project and a 2-module reactor, and assert per-test class
 * attribution is correct — including across the module boundary (FR-011).
 */
class DependencyTrackingIntegrationTest {

    @Test
    void singleModuleClassAttributionIsCorrect(@TempDir Path projectDir, @TempDir Path outDir) throws Exception {
        Path agentJar = findOwnAgentJar();
        Path recordFile = outDir.resolve("deps.json");

        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.addSystemDependency(null, agentJar);
        fixture.writeClass("com.example.Foo",
                "package com.example; public class Foo { public int value() { return 42; } }");
        fixture.writeTest("com.example.FooTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class FooTest {
                    @Test
                    void checksValue() {
                        assertEquals(42, new Foo().value());
                    }
                }
                """);
        fixture.commit("initial");

        runMvnTest(projectDir, agentJar, recordFile);

        Map<TestIdentity, Map<String, String>> recorded = new DependencyRecordReader().readAll(recordFile);
        TestIdentity fooTest = new TestIdentity("com.example.FooTest", "checksValue");

        assertTrue(recorded.containsKey(fooTest), "expected an entry for " + fooTest + " in " + recorded.keySet());
        assertTrue(recorded.get(fooTest).containsKey("com.example.Foo"),
                "FooTest's recorded dependencies must include com.example.Foo");
    }

    @Test
    void crossModuleAttributionIsCorrect(@TempDir Path projectDir, @TempDir Path outDir) throws Exception {
        Path agentJar = findOwnAgentJar();
        Path recordFile = outDir.resolve("deps.json");

        FixtureProjectBuilder fixture = FixtureProjectBuilder.twoModuleReactor(projectDir);
        fixture.addSystemDependency("moduleB", agentJar);
        fixture.writeClassInModule("moduleA", "com.example.a.Foo",
                "package com.example.a; public class Foo { public int value() { return 7; } }");
        fixture.writeClassInModule("moduleB", "com.example.b.Consumer", """
                package com.example.b;
                import com.example.a.Foo;
                public class Consumer {
                    public int consume() { return new Foo().value(); }
                }
                """);
        fixture.writeTestInModule("moduleB", "com.example.b.ConsumerTest", """
                package com.example.b;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class ConsumerTest {
                    @Test
                    void checksConsume() {
                        assertEquals(7, new Consumer().consume());
                    }
                }
                """);
        fixture.commit("initial");

        // Run from the reactor ROOT so Maven resolves moduleB's dependency on moduleA via
        // the reactor session itself, with no separate `mvn install` needed.
        runMvnTest(projectDir, agentJar, recordFile);

        Map<TestIdentity, Map<String, String>> recorded = new DependencyRecordReader().readAll(recordFile);
        TestIdentity consumerTest = new TestIdentity("com.example.b.ConsumerTest", "checksConsume");

        assertTrue(recorded.containsKey(consumerTest),
                "expected an entry for " + consumerTest + " in " + recorded.keySet());
        assertTrue(recorded.get(consumerTest).containsKey("com.example.a.Foo"),
                "cross-module dependency must be attributed (FR-011): " + recorded.get(consumerTest));
    }

    private static Path findOwnAgentJar() throws IOException {
        Path targetDir = Path.of("target");
        try (var stream = Files.list(targetDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().matches("blastradius-core-.*\\.jar"))
                    .filter(p -> p.getFileName().toString().contains("-agent"))
                    .filter(p -> !p.getFileName().toString().contains("sources"))
                    .filter(p -> !p.getFileName().toString().contains("tests"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "agent jar not found in target/ — expected the process-test-classes-phase "
                                    + "jar execution to have produced it before tests run"));
        }
    }

    private static void runMvnTest(Path projectDir, Path agentJar, Path recordFile)
            throws IOException, InterruptedException {
        String argLine = "-javaagent:" + agentJar.toAbsolutePath() + "=" + recordFile.toAbsolutePath();
        ProcessBuilder pb = new ProcessBuilder(
                "mvn", "-B", "--no-transfer-progress", "test", "-DargLine=" + argLine)
                .directory(projectDir.toFile())
                .redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(3, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            fail("mvn test timed out against fixture project at " + projectDir);
        }
        if (process.exitValue() != 0) {
            fail("mvn test failed (exit " + process.exitValue() + ") against fixture project at "
                    + projectDir + ":\n" + output);
        }
    }
}
