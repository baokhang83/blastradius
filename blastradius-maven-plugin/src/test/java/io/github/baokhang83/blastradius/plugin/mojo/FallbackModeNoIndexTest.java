package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test (tasks.md T042, US4/FR-007): a PR/feature-branch build with no
 * persisted index yet (no trunk {@code TRACK} run has ever happened) must run in {@code
 * FALLBACK} mode — the full suite still runs safely, but deliberately no track subprocess
 * is forked and no index is produced, since a branch commit is a poor anchor for the
 * shared index (research.md #1).
 */
class FallbackModeNoIndexTest {

    @BeforeAll
    static void installThisPluginOnce() throws Exception {
        EndToEndTestSupport.installThisPluginOnce();
    }

    @Test
    void aPrBuildWithNoIndexFallsBackWithoutTracking(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = EndToEndTestSupport.seedFooBarFixture(projectDir);
        String anchorCommit = fixture.commit("initial");

        fixture.writeClass("com.example.Baz",
                "package com.example; public class Baz { public int value() { return 3; } }");
        fixture.commit("add unrelated Baz class");

        fixture.addBuildPlugin(null, EndToEndTestSupport.pluginXml(anchorCommit));

        String output = EndToEndTestSupport.runMvnTest(projectDir);

        assertTrue(output.contains("BUILD SUCCESS"), "expected the build to succeed:\n" + output);
        assertTrue(output.contains("Running com.example.FooTest"),
                "expected the full suite to run, including FooTest:\n" + output);
        assertTrue(output.contains("Running com.example.BarTest"),
                "expected the full suite to run, including BarTest:\n" + output);
        assertTrue(output.contains("[blastradius] FALLBACK"),
                "expected the console summary to report FALLBACK mode:\n" + output);

        Path indexFile = projectDir.resolve(".blastradius/index.json");
        assertFalse(Files.exists(indexFile),
                "expected no index to be written on a FALLBACK build (no track subprocess forked)");
    }
}
