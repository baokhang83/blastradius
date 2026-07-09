package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndexWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test (tasks.md T030, US2/FR-011): a change that breaks a class a selected
 * test depends on must still make that test run, fail, and fail the overall Maven build —
 * narrowing via {@link SurefireFilterApplier} must never suppress a real failure's effect
 * on the build outcome. Shares its fixture-driving machinery with {@link SelectModeEndToEndTest}
 * via {@link EndToEndTestSupport}.
 */
class SelectedTestFailurePropagatesTest {

    @BeforeAll
    static void installThisPluginOnce() throws Exception {
        EndToEndTestSupport.installThisPluginOnce();
    }

    @Test
    void aBrokenDependencyFailsTheBuild(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = EndToEndTestSupport.seedFooBarFixture(projectDir);
        String anchorCommit = fixture.commit("initial");

        DependencyIndex index = EndToEndTestSupport.trackDependencies(projectDir, anchorCommit);
        new DependencyIndexWriter().write(projectDir.resolve(".blastradius/index.json"), index);

        // Break Foo without updating FooTest's expectation — a real regression.
        fixture.writeClass("com.example.Foo",
                "package com.example; public class Foo { public int value() { return 2; } }");
        fixture.commit("break Foo");

        fixture.addBuildPlugin(null, EndToEndTestSupport.pluginXml(anchorCommit));

        String output = EndToEndTestSupport.runMvnTest(projectDir);

        assertTrue(output.contains("BUILD FAILURE"), "expected the build to fail:\n" + output);
        assertTrue(output.contains("Running com.example.FooTest"),
                "expected the dependent test to actually run:\n" + output);
        assertFalse(output.contains("Running com.example.BarTest"),
                "expected the unrelated test to still be skipped:\n" + output);
    }
}
