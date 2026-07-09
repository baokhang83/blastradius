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
     * pipeline: a class loaded only inside {@code @BeforeAll} (a container-level
     * callback, not a test) is never attributed to any test by
     * {@code TestBoundaryListener} (its {@code executionStarted} only fires for actual
     * tests) — so a dependency established solely during class-level setup is invisible
     * to tracking. This is a real, narrow, documented limitation of container-level
     * setup, not a contrived test artifact.
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
        fixture.writeTest("com.example.GapTest", """
                package com.example;
                import org.junit.jupiter.api.BeforeAll;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class GapTest {
                    @BeforeAll
                    static void warmUp() {
                        Shared.value(); // loads Shared before any @Test's executionStarted fires
                    }
                    @Test
                    void checksSharedValue() {
                        assertEquals(1, Shared.value()); // already loaded; transform() won't refire
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
