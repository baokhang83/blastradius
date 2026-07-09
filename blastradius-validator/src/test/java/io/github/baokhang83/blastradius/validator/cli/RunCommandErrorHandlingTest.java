package io.github.baokhang83.blastradius.validator.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exit-code-2 ("the run itself could not complete") and graceful-degradation behavior,
 * distinct from a FAIL verdict. {@code Main.main()} itself is not exercised here since it
 * calls {@code System.exit}, which would terminate the test JVM.
 */
class RunCommandErrorHandlingTest {

    @Test
    void invalidProjectPathIsRejectedAtConfigConstructionTime(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist");
        assertThrows(IllegalArgumentException.class,
                () -> new RunConfig(missing, 10, tempDir.resolve("report.json")));
    }

    @Test
    void unwritableReportOutputPathYieldsExitCodeTwo(@TempDir Path projectDir, @TempDir Path outDir)
            throws IOException {
        Path agentJar = findOwnAgentJar();
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.commit("initial");

        // The report's parent directory does not exist and cannot be created as a file path.
        Path unwritableReportPath = outDir.resolve("no-such-directory").resolve("nested").resolve("report.json");
        RunConfig config = new RunConfig(projectDir, 10, unwritableReportPath);

        int exitCode = new RunCommand().run(config, agentJar);

        assertEquals(2, exitCode);
    }

    @Test
    void nonMavenTargetDegradesGracefullyRatherThanCrashing(@TempDir Path projectDir, @TempDir Path outDir)
            throws Exception {
        Path agentJar = findOwnAgentJar();
        Path reportFile = outDir.resolve("report.json");

        // A valid git repo with real commits, but no pom.xml at all.
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        Files.delete(projectDir.resolve("pom.xml"));
        fixture.commit("initial (no pom.xml)");
        Files.writeString(projectDir.resolve("readme.txt"), "not a maven project");
        fixture.commit("second commit");

        RunConfig config = new RunConfig(projectDir, 1, reportFile);
        int exitCode = new RunCommand().run(config, agentJar);

        // Not a crash (no exit-2): the pair is correctly excluded as unbuildable, and an
        // empty analyzed set is a vacuous PASS, not a fatal error.
        assertEquals(0, exitCode);
        JsonNode report = new ObjectMapper().readTree(reportFile.toFile());
        assertEquals(1, report.get("excludedCommitPairs").size());
        assertTrue(report.get("analyzedCommitPairs").isEmpty());
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
