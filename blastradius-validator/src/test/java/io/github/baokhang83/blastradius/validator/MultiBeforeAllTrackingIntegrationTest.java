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
 * Multiple test classes whose failures depend only on {@code @BeforeAll}-loaded application
 * classes must all be selected. This verifies the container dependency is unioned into every
 * class member, rather than merely fixing the single-class case.
 */
class MultiBeforeAllTrackingIntegrationTest {

    @Test
    void beforeAllDependenciesAreTrackedForEveryContainer(@TempDir Path projectDir, @TempDir Path outDir)
            throws Exception {
        Path agentJar = findOwnAgentJar();
        Path reportFile = outDir.resolve("report.json");

        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.addSystemDependency(null, agentJar);
        fixture.writeClass("com.example.Shared",
                "package com.example; public class Shared { public static int value() { return 1; } }");
        // The markers give both tests non-empty tracked baselines (so neither is
        // safety-net-selected as "new/no baseline"). Shared is deliberately invoked only in
        // each class's @BeforeAll and cached, so the test method itself cannot re-attribute it.
        // The only way these failures can be selected is the container-level tracking added by
        // #219, followed by unioning the synthetic container dependency into its member test.
        fixture.writeClass("com.example.MarkerA", "package com.example; public class MarkerA {}");
        fixture.writeClass("com.example.MarkerB", "package com.example; public class MarkerB {}");
        // Runs first (alphabetical runOrder, see FixtureProjectBuilder's pom) to keep the
        // fixture's class-loading order deterministic.
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

        assertEquals(0, exitCode);
        JsonNode json = new ObjectMapper().readTree(reportFile.toFile());
        assertEquals("PASS", json.get("verdict").asText());
        assertEquals(2, json.get("failureCoverage").get("newlyConfirmedFailingTests").asInt());
        assertEquals(2, json.get("failureCoverage").get("selectedNewlyConfirmedFailures").asInt());
        assertEquals(0, json.get("failureCoverage").get("skippedNewlyConfirmedFailures").asInt());
        assertTrue(json.get("wouldMissCases").isEmpty());
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
