package io.github.baokhang83.blastradius.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.baokhang83.blastradius.validator.cli.RunCommand;
import io.github.baokhang83.blastradius.validator.cli.RunConfig;
import io.github.baokhang83.blastradius.validator.build.SkippedTests;
import io.github.baokhang83.blastradius.validator.git.HistoryMode;
import io.github.baokhang83.blastradius.validator.mutation.MutationValidationConfig;
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

    /**
     * Once {@code TestBoundaryListener} attributes {@code @BeforeAll}-loaded classes to their
     * container's tests (#219), {@code GapTest} correctly tracks {@code Shared} even though it's
     * only ever invoked from {@code @BeforeAll} — so a mutant of {@code Shared} correctly selects
     * {@code GapTest} as a killing test instead of skipping it. This test used to be named
     * {@code mutationValidationFailsWhenTheHistoricalPairWouldSkipAKillingTest} and asserted the
     * opposite (a skip/FAIL), documenting the gap before it closed.
     */
    @Test
    void mutationValidationSelectsABeforeAllTrackedKillingTest(
            @TempDir Path projectDir, @TempDir Path outDir) throws Exception {
        Path agentJar = findOwnAgentJar();
        Path reportFile = outDir.resolve("report.json");
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.addSystemDependency(null, agentJar);
        fixture.writeClass("com.example.Shared",
                "package com.example; public class Shared { public static boolean value() { return true; } }");
        fixture.writeTest("com.example.AaaWarmupTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                class AaaWarmupTest { @Test void warmsUp() {} }
                """);
        fixture.writeTest("com.example.GapTest", """
                package com.example;
                import org.junit.jupiter.api.BeforeAll;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertTrue;
                class GapTest {
                    static boolean cached;
                    @BeforeAll static void warmUp() { cached = Shared.value(); }
                    @Test void detectsFalse() { assertTrue(cached); }
                }
                """);
        fixture.commit("baseline");
        fixture.writeClass("com.example.Unrelated",
                "package com.example; public class Unrelated { public int value() { return 1; } }");
        fixture.commit("real historical change");

        RunConfig config = new RunConfig(projectDir, 1, reportFile, null, false, 1,
                RunConfig.DEFAULT_BUILD_TIMEOUT_MINUTES, false, HistoryMode.ALL_PARENTS, SkippedTests.none(),
                new MutationValidationConfig("com.example.Shared", 1, 1, 10));
        int exitCode = new RunCommand().run(config, agentJar);

        JsonNode report = new ObjectMapper().readTree(reportFile.toFile());
        assertEquals(0, exitCode);
        assertEquals("PASS", report.get("verdict").asText());
        assertEquals("PASS", report.get("mutationValidation").get("verdict").asText());
        assertEquals(1, report.get("mutationValidation").get("coverage").get("selectedKillingTests").asInt());
        assertEquals(0, report.get("mutationValidation").get("coverage").get("skippedKillingTests").asInt());
        assertEquals("com.example.GapTest", report.get("mutationValidation").get("experiments").get(0)
                .get("selectedKillingTests").get(0).get("className").asText());
    }

    @Test
    void mutationValidationPassesWhenTheHistoricalPairSelectsAKillingTest(
            @TempDir Path projectDir, @TempDir Path outDir) throws Exception {
        Path agentJar = findOwnAgentJar();
        Path reportFile = outDir.resolve("report.json");
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.addSystemDependency(null, agentJar);
        fixture.writeClass("com.example.Flag",
                "package com.example; public class Flag { public boolean value() { return true; } }");
        fixture.writeTest("com.example.FlagTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertTrue;
                class FlagTest { @Test void detectsFalse() { assertTrue(new Flag().value()); } }
                """);
        fixture.commit("baseline");
        fixture.writeClass("com.example.Unrelated",
                "package com.example; public class Unrelated { public int value() { return 1; } }");
        fixture.commit("real historical change");

        RunConfig config = new RunConfig(projectDir, 1, reportFile, null, false, 1,
                RunConfig.DEFAULT_BUILD_TIMEOUT_MINUTES, false, HistoryMode.ALL_PARENTS, SkippedTests.none(),
                new MutationValidationConfig("com.example.Flag", 1, 1, 10));
        int exitCode = new RunCommand().run(config, agentJar);

        JsonNode report = new ObjectMapper().readTree(reportFile.toFile());
        assertEquals(0, exitCode);
        assertEquals("PASS", report.get("mutationValidation").get("verdict").asText());
        assertEquals(1, report.get("mutationValidation").get("coverage").get("selectedKillingTests").asInt());
        assertEquals(0, report.get("mutationValidation").get("coverage").get("skippedKillingTests").asInt());
    }

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
     * {@code Shared} is invoked <em>only</em> from {@code @BeforeAll} (a container-level
     * callback, not a test) and its result cached into a static field — the {@code @Test}
     * itself never calls {@code Shared} again. Before #219, {@code TestBoundaryListener} only
     * attributed a load when a test method was actually current, so this dependency stayed
     * invisible and this test documented the resulting would-miss. Since #219, {@code
     * @BeforeAll} runs under its container's synthetic identity and that identity's
     * dependencies get unioned into every member test, so {@code GapTest} now correctly
     * tracks {@code Shared} and gets selected when it changes. This test used to be named
     * {@code untrackedBeforeAllDependencyProducesAFailVerdict} and asserted the opposite
     * (a FAIL/would-miss), documenting the gap before it closed.
     */
    @Test
    void trackedBeforeAllDependencyProducesAPassVerdict(@TempDir Path projectDir, @TempDir Path outDir)
            throws Exception {
        Path agentJar = findOwnAgentJar();
        Path reportFile = outDir.resolve("report.json");

        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.addSystemDependency(null, agentJar);
        fixture.writeClass("com.example.Shared",
                "package com.example; public class Shared { public static int value() { return 1; } }");
        // Runs first (alphabetical runOrder, see FixtureProjectBuilder's pom). Since #219 the
        // agent's once-per-fork ambient snapshot is taken at the first *container* start, before
        // any @BeforeAll runs, so this ordering is no longer load-bearing for correctness — kept
        // anyway so the fork's very first class-load isn't GapTest's own @BeforeAll.
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

        // Shared.java changes and breaks GapTest. GapTest's tracked baseline now includes
        // Shared, via #219's container-level attribution of its @BeforeAll load, so this
        // real failure gets correctly selected instead of missed.
        fixture.writeClass("com.example.Shared",
                "package com.example; public class Shared { public static int value() { return 2; } }");
        fixture.commit("break Shared");

        RunConfig config = new RunConfig(projectDir, 1, reportFile);
        int exitCode = new RunCommand().run(config, agentJar);

        assertEquals(0, exitCode, "expected PASS verdict (exit code 0)");
        JsonNode report = new ObjectMapper().readTree(reportFile.toFile());
        assertEquals("PASS", report.get("verdict").asText());
        assertEquals("ALL_PARENTS", report.get("historyMode").asText());
        assertEquals(1, report.get("failureCoverage").get("pairsWithNewlyConfirmedFailures").asInt());
        assertEquals(1, report.get("failureCoverage").get("newlyConfirmedFailingTests").asInt());
        assertEquals(1, report.get("failureCoverage").get("selectedNewlyConfirmedFailures").asInt());
        assertEquals(0, report.get("failureCoverage").get("skippedNewlyConfirmedFailures").asInt());
        assertEquals(0, report.get("wouldMissCases").size());
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
