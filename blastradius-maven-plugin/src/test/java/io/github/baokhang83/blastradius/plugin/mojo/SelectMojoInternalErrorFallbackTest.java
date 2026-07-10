package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex.TestDependencyEntry;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndexWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test (tasks.md T050, spec.md Edge Case "the plugin's own selection computation
 * encounters an internal error"): an otherwise-{@code APPLICABLE} persisted index whose
 * contents are corrupted in a way {@link io.github.baokhang83.blastradius.plugin.index.
 * IndexApplicabilityResolver} cannot detect (readable JSON, reachable anchor, but a duplicate
 * {@link TestIdentity} key that only surfaces as a fault once {@code SelectMojo}'s own
 * SELECT-branch computation actually consumes it) must not crash the build or silently skip
 * tests — the goal falls back to a full, unfiltered run instead.
 */
class SelectMojoInternalErrorFallbackTest {

    @BeforeAll
    static void installThisPluginOnce() throws Exception {
        EndToEndTestSupport.installThisPluginOnce();
    }

    @Test
    void aCorruptedIndexFallsBackInsteadOfCrashing(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = EndToEndTestSupport.seedFooBarFixture(projectDir);
        String anchorCommit = fixture.commit("initial");

        TestIdentity fooTest = new TestIdentity("com.example.FooTest", "checksFoo");
        DependencyIndex corruptedIndex = new DependencyIndex(anchorCommit, Instant.now().toString(), List.of(
                new TestDependencyEntry(fooTest, Set.of("com.example.Foo")),
                new TestDependencyEntry(fooTest, Set.of("com.example.Foo"))));
        new DependencyIndexWriter().write(projectDir.resolve(".blastradius/index.json"), corruptedIndex);

        fixture.writeClass("com.example.Baz",
                "package com.example; public class Baz { public int value() { return 3; } }");
        fixture.commit("add unrelated Baz class");

        fixture.addBuildPlugin(null, EndToEndTestSupport.pluginXml(anchorCommit));

        String output = EndToEndTestSupport.runMvnTest(projectDir);

        assertTrue(output.contains("BUILD SUCCESS"),
                "expected the build to still succeed despite the corrupted index:\n" + output);
        assertTrue(output.contains("Running com.example.FooTest"),
                "expected the full suite to run despite the corrupted index, including FooTest:\n" + output);
        assertTrue(output.contains("Running com.example.BarTest"),
                "expected the full suite to run despite the corrupted index, including BarTest:\n" + output);
        assertTrue(output.contains("[blastradius] FALLBACK"),
                "expected the console summary to report a FALLBACK outcome:\n" + output);
        assertTrue(output.contains("INTERNAL_ERROR"),
                "expected the fallback reason to identify an internal error, not a normal index-applicability gap:\n"
                        + output);
    }
}
