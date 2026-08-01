package io.github.baokhang83.blastradius.validator.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MutationCommandIntegrationTest {

    @Test
    void reportsAPassWhenTheKillingTestTracksTheMutatedClass(@TempDir Path project, @TempDir Path outDir)
            throws Exception {
        Path agentJar = findOwnAgentJar();
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(project);
        fixture.addSystemDependency(null, agentJar);
        fixture.writeClass("com.example.Flag",
                "package com.example; public class Flag { public boolean value() { return true; } }");
        fixture.writeTest("com.example.FlagTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertTrue;
                class FlagTest {
                    @Test void detectsFalse() { assertTrue(new Flag().value()); }
                }
                """);
        fixture.commit("baseline");
        Path reportFile = outDir.resolve("mutation.json");

        int exitCode = new MutationCommand().run(config(project, reportFile, "com.example.Flag"), agentJar);

        MutationReport report = new ObjectMapper().readValue(reportFile.toFile(), MutationReport.class);
        assertEquals(0, exitCode);
        assertEquals(1, report.coverage().testKilledMutants());
        assertEquals(1, report.coverage().selectedKillingTests());
        assertEquals(0, report.coverage().skippedKillingTests());
        assertFalse(Files.readString(project.resolve("src/main/java/com/example/Flag.java")).contains("return false"));
    }

    @Test
    void reportsAFailWhenAConfirmedKillingTestWasNotTracked(@TempDir Path project, @TempDir Path outDir)
            throws Exception {
        Path agentJar = findOwnAgentJar();
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(project);
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
                    @Test void detectsMutation() { assertTrue(cached); }
                }
                """);
        fixture.commit("baseline");
        Path reportFile = outDir.resolve("mutation.json");

        int exitCode = new MutationCommand().run(config(project, reportFile, "com.example.Shared"), agentJar);

        MutationReport report = new ObjectMapper().readValue(reportFile.toFile(), MutationReport.class);
        assertEquals(1, exitCode);
        assertEquals(1, report.coverage().testKilledMutants());
        assertEquals(1, report.coverage().skippedKillingTests());
        assertEquals("com.example.GapTest", report.experiments().getFirst().skippedKillingTests().getFirst().className());
    }

    private MutationConfig config(Path project, Path report, String className) {
        return new MutationConfig(project, report, className, 1, 1, 10, null,
                MutationConfig.DEFAULT_BUILD_TIMEOUT_MINUTES, false);
    }

    private static Path findOwnAgentJar() throws IOException {
        Path targetDir = Path.of("target");
        try (var stream = Files.list(targetDir)) {
            return stream.filter(path -> path.getFileName().toString().matches("blastradius-validator-.*\\.jar"))
                    .filter(path -> !path.getFileName().toString().contains("sources"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("agent jar not found in target/"));
        }
    }
}
