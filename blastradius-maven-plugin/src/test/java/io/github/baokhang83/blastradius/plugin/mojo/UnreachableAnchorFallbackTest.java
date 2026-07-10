package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndexWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test (tasks.md T044, US4/FR-007): a persisted index whose {@code
 * anchorCommit} is no longer reachable in the project's git history (e.g. after a history
 * rewrite) must be treated as inapplicable — {@link
 * io.github.baokhang83.blastradius.plugin.index.IndexApplicabilityResolver} reports {@code
 * ANCHOR_UNREACHABLE} and {@code SelectMojo} falls back to a full, unfiltered run rather
 * than crashing or misapplying a stale selection.
 */
class UnreachableAnchorFallbackTest {

    private static final String UNREACHABLE_ANCHOR = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";

    @BeforeAll
    static void installThisPluginOnce() throws Exception {
        EndToEndTestSupport.installThisPluginOnce();
    }

    @Test
    void anUnreachableAnchorFallsBackInsteadOfCrashing(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = EndToEndTestSupport.seedFooBarFixture(projectDir);
        String anchorCommit = fixture.commit("initial");

        DependencyIndex staleIndex = new DependencyIndex(UNREACHABLE_ANCHOR, Instant.now().toString(), List.of());
        new DependencyIndexWriter().write(projectDir.resolve(".blastradius/index.json"), staleIndex);

        fixture.writeClass("com.example.Baz",
                "package com.example; public class Baz { public int value() { return 3; } }");
        fixture.commit("add unrelated Baz class");

        fixture.addBuildPlugin(null, EndToEndTestSupport.pluginXml(anchorCommit));

        String output = EndToEndTestSupport.runMvnTest(projectDir);

        assertTrue(output.contains("BUILD SUCCESS"), "expected the build to succeed:\n" + output);
        assertTrue(output.contains("Running com.example.FooTest"),
                "expected the full suite to run despite the unreachable-anchor index, including FooTest:\n" + output);
        assertTrue(output.contains("Running com.example.BarTest"),
                "expected the full suite to run despite the unreachable-anchor index, including BarTest:\n" + output);
        assertTrue(output.contains("[blastradius] FALLBACK"),
                "expected the console summary to report FALLBACK mode:\n" + output);
        assertTrue(output.toLowerCase().contains("anchor"),
                "expected the fallback reason to mention the unreachable anchor:\n" + output);
    }
}
