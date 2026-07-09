package io.github.baokhang83.blastradius.validator.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Measures the dependency-tracking agent's wall-clock overhead against spec.md's SC-006
 * target (no more than ~20% over the target project's native {@code mvn test} time).
 *
 * <p>This is a small-fixture sanity measurement, not a rigorous benchmark — a tiny
 * project's build time is dominated by fixed JVM/Maven startup cost, not by the agent
 * itself, so the bound here is intentionally generous to avoid environment-driven
 * flakiness. The actual measured overhead is printed for inspection; a real read on
 * SC-006 needs a real-world project (see quickstart.md / T061).
 */
class AgentOverheadMeasurementTest {

    @Test
    void agentOverheadIsWithinAToleratedBound(@TempDir Path projectDir, @TempDir Path outDir) throws IOException {
        Path agentJar = findOwnAgentJar();
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        for (int i = 0; i < 10; i++) {
            fixture.writeClass("com.example.Class" + i,
                    "package com.example; public class Class" + i + " { public int value() { return " + i + "; } }");
            fixture.writeTest("com.example.Class" + i + "Test", """
                    package com.example;
                    import org.junit.jupiter.api.Test;
                    import static org.junit.jupiter.api.Assertions.assertEquals;
                    class Class%dTest {
                        @Test
                        void checksValue() {
                            assertEquals(%d, new Class%d().value());
                        }
                    }
                    """.formatted(i, i, i));
        }
        fixture.commit("initial");

        MavenBuildRunner runner = new MavenBuildRunner();

        long withoutAgentMs = time(() -> runner.run(projectDir, null, null));
        long withAgentMs = time(() -> runner.run(projectDir, agentJar, outDir.resolve("deps.json")));

        double overhead = (withAgentMs - withoutAgentMs) / (double) withoutAgentMs;
        System.out.printf("Agent overhead: without=%dms with=%dms overhead=%.1f%%%n",
                withoutAgentMs, withAgentMs, overhead * 100);

        // Generous bound for a tiny fixture project dominated by fixed JVM/Maven startup
        // cost; SC-006's ~20% target is validated for real against a real project (T061).
        assertTrue(overhead < 3.0, "agent overhead was unexpectedly extreme: " + (overhead * 100) + "%");
    }

    private static long time(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static Path findOwnAgentJar() throws IOException {
        Path targetDir = Path.of("target");
        try (var stream = Files.list(targetDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().matches("blastradius-validator-.*\\.jar"))
                    .filter(p -> !p.getFileName().toString().contains("sources"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("agent jar not found in target/"));
        }
    }
}
