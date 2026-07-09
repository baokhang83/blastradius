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
 * Confirms the report's {@code savingsSummary} numbers match hand-computed expectations
 * for a real run (FR-008 / SC-003).
 */
class SavingsSummaryIntegrationTest {

    @Test
    void savingsSummaryReflectsWhichTestsWereActuallySelected(@TempDir Path projectDir, @TempDir Path outDir)
            throws Exception {
        Path agentJar = findOwnAgentJar();
        Path reportFile = outDir.resolve("report.json");

        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.addSystemDependency(null, agentJar);
        fixture.writeClass("com.example.Foo",
                "package com.example; public class Foo { public int value() { return 1; } }");
        fixture.writeClass("com.example.Bar",
                "package com.example; public class Bar { public int value() { return 2; } }");
        fixture.writeTest("com.example.FooTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class FooTest {
                    @Test
                    void checksFoo() {
                        assertEquals(1, new Foo().value());
                    }
                }
                """);
        fixture.writeTest("com.example.BarTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class BarTest {
                    @Test
                    void checksBar() {
                        assertEquals(2, new Bar().value());
                    }
                }
                """);
        fixture.commit("initial");

        // Only Foo changes — FooTest should be selected via DEPENDENCY_MATCH; BarTest
        // has no reason to be selected (NO_MATCH), so exactly 1 of 2 tests is selected.
        fixture.writeClass("com.example.Foo",
                "package com.example; public class Foo { public int value() { return 100; } }");
        fixture.commit("change Foo only");

        RunConfig config = new RunConfig(projectDir, 1, reportFile);
        new RunCommand().run(config, agentJar);

        JsonNode report = new ObjectMapper().readTree(reportFile.toFile());
        JsonNode savings = report.get("savingsSummary");

        assertEquals(2, savings.get("totalTestExecutions").asInt());
        assertEquals(1, savings.get("totalSelected").asInt());
        assertEquals(0.5, savings.get("proportionSkipped").asDouble(), 1e-9);
        assertEquals(1, savings.get("dependencyMatchedSelections").asInt());
        assertEquals(0, savings.get("fallbackDrivenSelections").asInt());
        assertTrue(savings.get("newOrModifiedTestSelections").asInt() >= 0);
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
