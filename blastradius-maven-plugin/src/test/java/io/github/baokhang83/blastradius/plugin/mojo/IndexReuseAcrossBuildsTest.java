package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test (tasks.md T043, US4/SC-005): a real {@code TRACK} build's own
 * produced index — not {@link EndToEndTestSupport}'s test-only shortcut — must survive
 * and be genuinely reused by a later {@code SELECT} build, proving the two branches
 * actually compose across separate invocations rather than only in isolation.
 */
class IndexReuseAcrossBuildsTest {

    @BeforeAll
    static void installThisPluginOnce() throws Exception {
        EndToEndTestSupport.installThisPluginOnce();
    }

    @Test
    void aTrackBuildsIndexThatALaterSelectBuildReuses(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = EndToEndTestSupport.seedFooBarFixture(projectDir);
        String anchorCommit = fixture.commit("initial");
        fixture.addBuildPlugin(null, EndToEndTestSupport.pluginXml(anchorCommit));

        // Build 1: a base-ref build with no persisted index — TRACK mode, real subprocess.
        String trackOutput = EndToEndTestSupport.runMvnTest(projectDir);
        assertTrue(trackOutput.contains("BUILD SUCCESS"), "expected the track build to succeed:\n" + trackOutput);
        assertTrue(trackOutput.contains("[blastradius] TRACK"),
                "expected the first build to report TRACK mode:\n" + trackOutput);

        // The plugin binding above was applied directly to the working tree (uncommitted),
        // deliberately never part of anchorCommit's own history (needed before build 1 could
        // even run it). Revert it here, before the next commit — otherwise commit()'s blanket
        // `git add .` would sweep this uncommitted pom.xml edit into "change Foo"'s diff too,
        // spuriously classifying it as a NON_SOURCE change and triggering the conservative
        // fallback rule for every test (FR-006) instead of the dependency match this test means
        // to exercise.
        fixture.git().checkout().addPath("pom.xml").call();

        // Build 2: a small, contained follow-up change on top of the now-tracked baseline.
        fixture.writeClass("com.example.Foo",
                "package com.example; public class Foo { public int value() { return 99; } }");
        fixture.writeTest("com.example.FooTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class FooTest {
                    @Test
                    void checksFoo() { assertEquals(99, new Foo().value()); }
                }
                """);
        fixture.commit("change Foo");
        fixture.addBuildPlugin(null, EndToEndTestSupport.pluginXml(anchorCommit));

        String selectOutput = EndToEndTestSupport.runMvnTest(projectDir);

        assertTrue(selectOutput.contains("BUILD SUCCESS"), "expected the select build to succeed:\n" + selectOutput);
        assertTrue(selectOutput.contains("[blastradius] SELECT"),
                "expected the second build to reuse the real tracked index and route to SELECT, not FALLBACK:\n"
                        + selectOutput);
        assertTrue(selectOutput.contains("Running com.example.FooTest"),
                "expected the modified test to run:\n" + selectOutput);
        assertFalse(selectOutput.contains("Running com.example.BarTest"),
                "expected the unrelated test to be skipped:\n" + selectOutput);
    }
}
