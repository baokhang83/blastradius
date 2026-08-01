package io.github.baokhang83.blastradius.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.baokhang83.blastradius.validator.cli.RunCommand;
import io.github.baokhang83.blastradius.validator.cli.RunConfig;
import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end: run the full pipeline (git traversal, non-destructive checkout, dependency
 * tracking, ground truth, selection, would-miss comparison, verdict, report) against a
 * real {@link FixtureProjectBuilder} project, exercising a PASS scenario. This is the
 * point at which Constitution Principle V's shadow-mode gate can first be exercised for
 * real (User Story 1, the MVP).
 */
class EndToEndVerdictIntegrationTest {

    @Test
    void unrelatedChangeAcrossCommitsProducesAPassVerdict(@TempDir Path projectDir, @TempDir Path outDir)
            throws Exception {
        Path agentJar = findOwnAgentJar();
        Path reportFile = outDir.resolve("report.json");

        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.addSystemDependency(null, agentJar);
        fixture.writeClass("com.example.Foo",
                "package com.example; public class Foo { public int value() { return 1; } }");
        fixture.writeTest("com.example.FooTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class FooTest {
                    @Test
                    void checksValue() {
                        assertEquals(1, new Foo().value());
                    }
                }
                """);
        fixture.commit("initial");

        // Second commit changes an UNRELATED class — FooTest's tracked dependency on Foo
        // is untouched, so it correctly should NOT be selected, and it still passes.
        fixture.writeClass("com.example.Unrelated",
                "package com.example; public class Unrelated { public int value() { return 99; } }");
        fixture.commit("add unrelated class");

        RunConfig config = new RunConfig(projectDir, 1, reportFile);
        int exitCode = new RunCommand().run(config, agentJar);

        assertEquals(0, exitCode, "expected PASS verdict (exit code 0)");
        JsonNode report = new ObjectMapper().readTree(reportFile.toFile());
        assertEquals("PASS", report.get("verdict").asText());
        assertTrue(report.get("wouldMissCases").isEmpty());
        assertEquals(1, report.get("analyzedCommitPairs").size());
    }

    /**
     * A deterministic, honest way to trigger a real would-miss through the actual
     * pipeline: {@code Shared} is invoked <em>only</em> from {@code @BeforeAll} (a
     * container-level callback, not a test) and its result cached into a static field —
     * the {@code @Test} itself never calls {@code Shared} again, so the class never
     * executes while {@code TestBoundaryListener} reports a current test. The agent's
     * runtime-use callback (injected into every project class at its first load,
     * regardless of whether a test is running) only attributes an execution when a test
     * is actually current, so a dependency exercised solely during class-level setup
     * stays invisible to tracking. This is a real, narrow, documented limitation of
     * container-level setup, not a contrived test artifact — it would stop being
     * "untracked" the moment the test body itself calls {@code Shared} again.
     */
    @Test
    void untrackedBeforeAllDependencyProducesAFailVerdict(@TempDir Path projectDir, @TempDir Path outDir)
            throws Exception {
        Path agentJar = findOwnAgentJar();
        Path reportFile = outDir.resolve("report.json");

        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.addSystemDependency(null, agentJar);
        fixture.writeClass("com.example.Shared",
                "package com.example; public class Shared { public static int value() { return 1; } }");
        // Runs first (alphabetical runOrder, see FixtureProjectBuilder's pom), so the
        // agent's once-per-fork ambient snapshot is already taken by the time GapTest's
        // own @BeforeAll loads Shared — otherwise that first-in-fork timing would itself
        // put Shared in the ambient set and mask the very gap this test documents.
        fixture.writeTest("com.example.AaaWarmupTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                class AaaWarmupTest {
                    @Test
                    void warmsUpTheFork() {}
                }
                """);
        fixture.writeTest("com.example.GapTest", """
                package com.example;
                import org.junit.jupiter.api.BeforeAll;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class GapTest {
                    static int cached;
                    @BeforeAll
                    static void warmUp() {
                        cached = Shared.value(); // the only place Shared is ever invoked
                    }
                    @Test
                    void checksSharedValue() {
                        assertEquals(1, cached); // reads the cached value; never calls Shared again
                    }
                }
                """);
        fixture.commit("initial");

        // Shared.java changes and breaks GapTest, but GapTest's tracked baseline never
        // included Shared (it was only ever loaded during the untracked @BeforeAll).
        fixture.writeClass("com.example.Shared",
                "package com.example; public class Shared { public static int value() { return 2; } }");
        fixture.commit("break Shared");

        RunConfig config = new RunConfig(projectDir, 1, reportFile);
        int exitCode = new RunCommand().run(config, agentJar);

        assertEquals(1, exitCode, "expected FAIL verdict (exit code 1)");
        JsonNode report = new ObjectMapper().readTree(reportFile.toFile());
        assertEquals("FAIL", report.get("verdict").asText());
        assertEquals("ALL_PARENTS", report.get("historyMode").asText());
        assertEquals(1, report.get("failureCoverage").get("pairsWithNewlyConfirmedFailures").asInt());
        assertEquals(1, report.get("failureCoverage").get("newlyConfirmedFailingTests").asInt());
        assertEquals(0, report.get("failureCoverage").get("selectedNewlyConfirmedFailures").asInt());
        assertEquals(1, report.get("failureCoverage").get("skippedNewlyConfirmedFailures").asInt());
        assertEquals(1, report.get("wouldMissCases").size());
        assertEquals("com.example.GapTest", report.get("wouldMissCases").get(0).get("test").get("className").asText());
    }

    /**
     * A test that was already failing at the BASE commit, for reasons entirely unrelated
     * to this pair's diff (real-world example: shenyu-admin's {@code
     * RoleMapperTest#testSelectAll}, broken by schema.sql's seed data on every commit).
     * Selection had nothing to catch — the failure predates the change — so it must not
     * be reported as a would-miss.
     */
    @Test
    void testAlreadyFailingAtBaseCommitProducesNoWouldMissCase(@TempDir Path projectDir, @TempDir Path outDir)
            throws Exception {
        Path agentJar = findOwnAgentJar();
        Path reportFile = outDir.resolve("report.json");

        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.addSystemDependency(null, agentJar);
        fixture.writeClass("com.example.Foo",
                "package com.example; public class Foo { public int value() { return 1; } }");
        fixture.writeTest("com.example.AlwaysBrokenTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class AlwaysBrokenTest {
                    @Test
                    void neverPasses() {
                        assertEquals(1, 2);
                    }
                }
                """);
        fixture.commit("initial");

        // Second commit changes an unrelated class — AlwaysBrokenTest was never going to
        // pass either way, before or after.
        fixture.writeClass("com.example.Foo",
                "package com.example; public class Foo { public int value() { return 2; } }");
        fixture.commit("change Foo");

        RunConfig config = new RunConfig(projectDir, 1, reportFile);
        int exitCode = new RunCommand().run(config, agentJar);

        assertEquals(0, exitCode, "expected PASS verdict (exit code 0): a pre-existing failure is not a would-miss");
        JsonNode report = new ObjectMapper().readTree(reportFile.toFile());
        assertEquals("PASS", report.get("verdict").asText());
        assertTrue(report.get("wouldMissCases").isEmpty());
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
