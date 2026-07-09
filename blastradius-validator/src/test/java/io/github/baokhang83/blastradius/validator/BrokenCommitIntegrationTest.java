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
 * A historical range containing one deliberately unbuildable commit must still complete,
 * with that pair excluded (and a reason recorded) rather than aborting the run or
 * corrupting the verdict/savings (FR-009).
 */
class BrokenCommitIntegrationTest {

    @Test
    void unbuildableHeadCommitIsExcludedNotFatal(@TempDir Path projectDir, @TempDir Path outDir) throws Exception {
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
        fixture.commit("initial (good)");

        // The head commit of this pair does not compile at all.
        fixture.writeClass("com.example.Foo", "package com.example; this is not valid java {{{");
        fixture.commit("broken (bad)");

        RunConfig config = new RunConfig(projectDir, 1, reportFile);
        int exitCode = new RunCommand().run(config, agentJar);

        // The run must complete (not throw/crash) and produce a verdict either way.
        assertTrue(exitCode == 0 || exitCode == 1, "run must complete with a real verdict, got exit " + exitCode);

        JsonNode report = new ObjectMapper().readTree(reportFile.toFile());
        assertEquals(0, report.get("analyzedCommitPairs").size(), "the only pair is unbuildable, so nothing was analyzed");
        assertEquals(1, report.get("excludedCommitPairs").size());
        assertTrue(report.get("excludedCommitPairs").get(0).has("exclusionReason"));
        assertEquals(0, report.get("wouldMissCases").size());
        assertEquals(0, report.get("savingsSummary").get("totalTestExecutions").asInt(),
                "an excluded pair must not contribute to the savings summary");
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
