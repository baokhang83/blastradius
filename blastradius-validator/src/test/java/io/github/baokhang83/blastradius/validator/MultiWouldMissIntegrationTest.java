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
        // MarkerB gives GapBTest a non-empty tracked baseline (so it isn't treated as
        // "new/no baseline" and safety-net-selected for that reason instead) while
        // Shared remains untracked for both — already loaded via GapATest's @BeforeAll
        // by the time GapBTest runs in the same JVM fork, so no fresh transform() event
        // fires for it there either. Both tests should genuinely miss when Shared breaks.
        fixture.writeClass("com.example.MarkerB", "package com.example; public class MarkerB {}");
        fixture.writeTest("com.example.GapATest", """
                package com.example;
                import org.junit.jupiter.api.BeforeAll;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class GapATest {
                    @BeforeAll
                    static void warmUp() { Shared.value(); }
                    @Test
                    void checksSharedA() { assertEquals(1, Shared.value()); }
                }
                """);
        fixture.writeTest("com.example.GapBTest", """
                package com.example;
                import org.junit.jupiter.api.BeforeAll;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class GapBTest {
                    @BeforeAll
                    static void warmUp() { Shared.value(); }
                    @Test
                    void checksSharedB() {
                        new MarkerB();
                        assertEquals(1, Shared.value());
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
