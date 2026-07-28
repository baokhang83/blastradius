package io.github.baokhang83.blastradius.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.baokhang83.blastradius.validator.cli.RunCommand;
import io.github.baokhang83.blastradius.validator.cli.RunConfig;
import io.github.baokhang83.blastradius.validator.report.AnalysisReport;
import io.github.baokhang83.blastradius.validator.report.TextSummaryRenderer;
import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import io.github.baokhang83.blastradius.validator.verdict.Verdict;
import io.github.baokhang83.blastradius.validator.verdict.WouldMissCase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A FAIL run with MULTIPLE would-miss cases must have every one present, individually,
 * in both the JSON report and the rendered text summary — none aggregated or summarized
 * away (spec.md US3 acceptance scenario 2).
 */
class MultiWouldMissIntegrationTest {

    @Test
    void everyWouldMissCaseSurvivesInBothJsonAndText(@TempDir Path projectDir, @TempDir Path outDir)
            throws Exception {
        Path agentJar = findOwnAgentJar();
        Path reportFile = outDir.resolve("report.json");

        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.addSystemDependency(null, agentJar);
        fixture.writeClass("com.example.Shared",
                "package com.example; public class Shared { public static int value() { return 1; } }");
        // The markers give both tests non-empty tracked baselines (so neither is
        // safety-net-selected as "new/no baseline"). They are loaded with Class.forName
        // inside the test bodies: a direct bytecode reference can be eagerly resolved
        // while the test class is discovered, before the agent has a current test
        // identity. Shared remains untracked because each class's @BeforeAll is the
        // only place that ever invokes it — its result is cached into a static field, so
        // the @Test body itself never calls Shared again. The agent's runtime-use
        // callback (injected at every project class's first load) only attributes an
        // execution when a test is current, so a dependency exercised solely from
        // @BeforeAll never re-fires it once a test starts.
        fixture.writeClass("com.example.MarkerA", "package com.example; public class MarkerA {}");
        fixture.writeClass("com.example.MarkerB", "package com.example; public class MarkerB {}");
        // Runs first (alphabetical runOrder, see FixtureProjectBuilder's pom), so the
        // agent's once-per-fork ambient snapshot is already taken before GapATest's own
        // @BeforeAll loads Shared — otherwise that first-in-fork timing would itself put
        // Shared in the ambient set and mask the gap both Gap tests below rely on.
        fixture.writeTest("com.example.AaaWarmupTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                class AaaWarmupTest {
                    @Test
                    void warmsUpTheFork() {}
                }
                """);
        fixture.writeTest("com.example.GapATest", """
                package com.example;
                import org.junit.jupiter.api.BeforeAll;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class GapATest {
                    static int cached;
                    @BeforeAll
                    static void warmUp() { cached = Shared.value(); }
                    @Test
                    void checksSharedA() throws ClassNotFoundException {
                        Class.forName("com.example.MarkerA");
                        assertEquals(1, cached);
                    }
                }
                """);
        fixture.writeTest("com.example.GapBTest", """
                package com.example;
                import org.junit.jupiter.api.BeforeAll;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class GapBTest {
                    static int cached;
                    @BeforeAll
                    static void warmUp() { cached = Shared.value(); }
                    @Test
                    void checksSharedB() throws ClassNotFoundException {
                        Class.forName("com.example.MarkerB");
                        assertEquals(1, cached);
                    }
                }
                """);
        fixture.commit("initial");

        fixture.writeClass("com.example.Shared",
                "package com.example; public class Shared { public static int value() { return 2; } }");
        fixture.commit("break Shared");

        RunConfig config = new RunConfig(projectDir, 1, reportFile);
        int exitCode = new RunCommand().run(config, agentJar);

        assertEquals(1, exitCode);
        JsonNode json = new ObjectMapper().readTree(reportFile.toFile());
        assertEquals("FAIL", json.get("verdict").asText());
        assertEquals(2, json.get("wouldMissCases").size(), "both would-miss cases must be present in JSON");

        List<String> classNamesInJson = json.get("wouldMissCases").findValuesAsText("className");
        // className is nested under test; findValuesAsText searches recursively.
        assertTrue(classNamesInJson.contains("com.example.GapATest"));
        assertTrue(classNamesInJson.contains("com.example.GapBTest"));

        AnalysisReport report = new ObjectMapper().readValue(reportFile.toFile(), AnalysisReport.class);
        assertEquals(2, report.wouldMissCases().size());
        assertEquals(Verdict.FAIL, report.verdict());

        String text = new TextSummaryRenderer().render(report);
        assertTrue(text.contains("com.example.GapATest#checksSharedA"), text);
        assertTrue(text.contains("com.example.GapBTest#checksSharedB"), text);
        assertTrue(text.contains("Would-miss cases: 2"), text);
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
