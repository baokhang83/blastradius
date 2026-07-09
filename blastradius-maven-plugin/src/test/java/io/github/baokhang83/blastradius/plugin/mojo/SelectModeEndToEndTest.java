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
 * End-to-end: a real fixture project, with {@code blastradius-maven-plugin} bound as an
 * actual build plugin, driven through a real {@code mvn test} subprocess — not a manually
 * constructed {@code MavenProject}. Subprocess-driving machinery lives in
 * {@link EndToEndTestSupport}, shared with US2's live-trust tests.
 */
class SelectModeEndToEndTest {

    @BeforeAll
    static void installThisPluginOnce() throws Exception {
        EndToEndTestSupport.installThisPluginOnce();
    }

    @Test
    void fewerTestsRunOnASmallContainedChange(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = EndToEndTestSupport.seedFooBarFixture(projectDir);
        String anchorCommit = fixture.commit("initial");

        // Build a real baseline index the same way TrackRunner does (subprocess mvn test,
        // agent attached), so the index reflects genuinely-observed dependencies.
        DependencyIndex index = EndToEndTestSupport.trackDependencies(projectDir, anchorCommit);
        new DependencyIndexWriter().write(projectDir.resolve(".blastradius/index.json"), index);

        // A small, contained change: only Foo — value AND its test's expectation both
        // updated consistently, so the change is genuinely correct (this test is about
        // selection narrowing, not about exercising a real failure — that's US2's job).
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

        String output = EndToEndTestSupport.runMvnTest(projectDir);

        assertTrue(output.contains("BUILD SUCCESS"), "expected the build to succeed:\n" + output);
        assertTrue(output.contains("Running com.example.FooTest"),
                "expected FooTest to actually run (modified test):\n" + output);
        assertFalse(output.contains("Running com.example.BarTest"),
                "expected BarTest to be skipped (no dependency match):\n" + output);
    }
}
