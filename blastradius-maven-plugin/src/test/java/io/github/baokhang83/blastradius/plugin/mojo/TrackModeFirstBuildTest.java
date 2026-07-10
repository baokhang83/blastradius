package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test (tasks.md T041, US4/FR-007): a project's very first build, with no
 * persisted index, built as the base reference itself, must run in {@code TRACK} mode —
 * the full suite runs completely unfiltered, and a fresh index is produced (via a real
 * {@code TrackRunner} subprocess, not {@link EndToEndTestSupport}'s test-only shortcut)
 * for later {@code SELECT} builds to use.
 */
class TrackModeFirstBuildTest {

    @BeforeAll
    static void installThisPluginOnce() throws Exception {
        EndToEndTestSupport.installThisPluginOnce();
    }

    @Test
    void aBaseRefBuildWithNoIndexTracksAndRunsEverything(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = EndToEndTestSupport.seedFooBarFixture(projectDir);
        String anchorCommit = fixture.commit("initial");

        fixture.addBuildPlugin(null, EndToEndTestSupport.pluginXml(anchorCommit));

        String output = EndToEndTestSupport.runMvnTest(projectDir);

        assertTrue(output.contains("BUILD SUCCESS"), "expected the build to succeed:\n" + output);
        assertTrue(output.contains("Running com.example.FooTest"),
                "expected the full suite to run, including FooTest:\n" + output);
        assertTrue(output.contains("Running com.example.BarTest"),
                "expected the full suite to run, including BarTest:\n" + output);
        assertTrue(output.contains("[blastradius] TRACK"),
                "expected the console summary to report TRACK mode:\n" + output);

        Path indexFile = projectDir.resolve(".blastradius/index.json");
        assertTrue(Files.exists(indexFile), "expected a fresh index to be written to " + indexFile);
        String indexJson = Files.readString(indexFile, StandardCharsets.UTF_8);
        assertTrue(indexJson.contains("\"anchorCommit\":\"" + anchorCommit + "\""),
                "expected the fresh index to be anchored to the tracked commit:\n" + indexJson);
        assertTrue(indexJson.contains("FooTest"), "expected the fresh index to record observed test dependencies:\n" + indexJson);
    }
}
