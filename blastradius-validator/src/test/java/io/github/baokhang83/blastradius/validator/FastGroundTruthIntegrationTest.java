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
 * {@code --fast-ground-truth} (RunConfig#fastGroundTruth) unifies build types and caches
 * per-commit results across the sliding window instead of building each commit's role
 * independently — these tests exercise that path end to end, through the same pipeline
 * {@link EndToEndVerdictIntegrationTest} exercises in the default, safe mode.
 */
class FastGroundTruthIntegrationTest {

    /**
     * A 3-commit, 2-pair window means the middle commit (c1) is BOTH pair 0's HEAD and
     * pair 1's BASE — exactly the cache-hit case the feature exists for. Correctness here
     * proves a cache hit doesn't feed a pair a stale or partial result.
     */
    @Test
    void middleCommitReusedAcrossPairsStillProducesACorrectPassVerdict(
            @TempDir Path projectDir, @TempDir Path outDir) throws Exception {
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

        fixture.writeClass("com.example.UnrelatedA",
                "package com.example; public class UnrelatedA { public int value() { return 1; } }");
        fixture.commit("add unrelated class A");

        fixture.writeClass("com.example.UnrelatedB",
                "package com.example; public class UnrelatedB { public int value() { return 2; } }");
        fixture.commit("add unrelated class B");

        RunConfig config = new RunConfig(projectDir, 2, reportFile, null, true);
        int exitCode = new RunCommand().run(config, agentJar);

        assertEquals(0, exitCode, "expected PASS verdict (exit code 0)");
        JsonNode report = new ObjectMapper().readTree(reportFile.toFile());
        assertEquals("PASS", report.get("verdict").asText());
        assertTrue(report.get("wouldMissCases").isEmpty());
        assertEquals(2, report.get("analyzedCommitPairs").size());
    }

    /**
     * Same would-miss fixture {@link EndToEndVerdictIntegrationTest} uses, run through
     * {@code --fast-ground-truth} instead: the ground-truth build is now agent-attached,
     * so this confirms attaching the agent to that build doesn't change pass/fail
     * detection for a test whose dependency was never tracked in the first place.
     */
    @Test
    void untrackedBeforeAllDependencyStillProducesAFailVerdict(
            @TempDir Path projectDir, @TempDir Path outDir) throws Exception {
        Path agentJar = findOwnAgentJar();
        Path reportFile = outDir.resolve("report.json");

        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.addSystemDependency(null, agentJar);
        fixture.writeClass("com.example.Shared",
                "package com.example; public class Shared { public static int value() { return 1; } }");
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
                        cached = Shared.value();
                    }
                    @Test
                    void checksSharedValue() {
                        assertEquals(1, cached);
                    }
                }
                """);
        fixture.commit("initial");

        fixture.writeClass("com.example.Shared",
                "package com.example; public class Shared { public static int value() { return 2; } }");
        fixture.commit("break Shared");

        RunConfig config = new RunConfig(projectDir, 1, reportFile, null, true);
        int exitCode = new RunCommand().run(config, agentJar);

        assertEquals(1, exitCode, "expected FAIL verdict (exit code 1)");
        JsonNode report = new ObjectMapper().readTree(reportFile.toFile());
        assertEquals("FAIL", report.get("verdict").asText());
        assertEquals(1, report.get("wouldMissCases").size());
        assertEquals("com.example.GapTest", report.get("wouldMissCases").get(0).get("test").get("className").asText());
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
