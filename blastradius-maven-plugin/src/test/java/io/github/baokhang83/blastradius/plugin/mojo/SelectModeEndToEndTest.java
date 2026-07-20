package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        EndToEndTestSupport.writeIndex(projectDir, index);

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

    @Test
    void kotlinFileFacadeChangeSelectsOnlyItsTrackedKotlinTest(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.enableKotlin();
        fixture.ignoreTargetDirectory();
        fixture.writeKotlinClass("com.example.Greeting", """
                package com.example
                fun greeting(): String = "hello"
                """);
        fixture.writeKotlinTest("com.example.GreetingTest", """
                package com.example
                import org.junit.jupiter.api.Assertions.assertEquals
                import org.junit.jupiter.api.Test
                class GreetingTest {
                    @Test fun checksGreeting() {
                        assertEquals("hello", greeting())
                    }
                }
                """);
        fixture.writeKotlinClass("com.example.Unrelated", """
                package com.example
                class Unrelated {
                    fun value(): Int = 7
                }
                """);
        fixture.writeKotlinTest("com.example.UnrelatedTest", """
                package com.example
                import org.junit.jupiter.api.Assertions.assertEquals
                import org.junit.jupiter.api.Test
                class UnrelatedTest {
                    @Test fun checksUnrelated() {
                        assertEquals(7, Unrelated().value())
                    }
                }
                """);
        String anchorCommit = fixture.commit("initial Kotlin fixture");

        DependencyIndex index = EndToEndTestSupport.trackDependencies(projectDir, anchorCommit);
        EndToEndTestSupport.writeIndex(projectDir, index);

        fixture.writeKotlinClass("com.example.Greeting", """
                package com.example
                fun greeting(): String = listOf("hello").single()
                """);
        fixture.commit("change Kotlin greeting");
        fixture.addBuildPlugin(null, EndToEndTestSupport.pluginXml(anchorCommit));

        String output = EndToEndTestSupport.runMvnTest(projectDir);

        assertTrue(output.contains("BUILD SUCCESS"), "expected the build to succeed:\n" + output);
        assertTrue(output.contains("Running com.example.GreetingTest"),
                "expected the affected Kotlin test to run:\n" + output);
        assertFalse(output.contains("Running com.example.UnrelatedTest"),
                "expected the independent Kotlin test to be skipped:\n" + output);
    }

    /**
     * US3 (tasks.md T034/T037/T040): the console summary and the persisted {@code
     * BuildReport} JSON must both reflect the actual decisions, live — not just in
     * {@code report}'s own unit tests.
     */
    @Test
    void reportAndConsoleSummaryReflectTheDecisions(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = EndToEndTestSupport.seedFooBarFixture(projectDir);
        String anchorCommit = fixture.commit("initial");

        DependencyIndex index = EndToEndTestSupport.trackDependencies(projectDir, anchorCommit);
        EndToEndTestSupport.writeIndex(projectDir, index);

        fixture.writeTest("com.example.FooTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class FooTest {
                    @Test
                    void checksFoo() { assertEquals(1, new Foo().value()); }
                    @Test
                    void anotherAssertion() { assertEquals(1, new Foo().value()); }
                }
                """);
        fixture.commit("modify FooTest only");

        fixture.addBuildPlugin(null, EndToEndTestSupport.pluginXml(anchorCommit));

        String output = EndToEndTestSupport.runMvnTest(projectDir);

        assertTrue(output.contains("BUILD SUCCESS"), "expected the build to succeed:\n" + output);
        assertTrue(output.contains("[blastradius] SELECT — index built from " + anchorCommit.substring(0, 7)),
                "expected the console header to name the index's anchor:\n" + output);
        assertTrue(output.contains("[blastradius] 2 / 3 tests selected (33.3% skipped)"),
                "expected the console summary to show 2 of 3 tests selected:\n" + output);
        assertTrue(output.contains("dependency-matched: 0, new-or-modified: 2, fallback: 0"),
                "expected the per-reason breakdown to attribute both to new-or-modified:\n" + output);

        Path reportFile = projectDir.resolve(".blastradius/last-build-report.json");
        assertTrue(Files.exists(reportFile), "expected a BuildReport to be written to " + reportFile);
        String reportJson = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertTrue(reportJson.contains("\"mode\":\"SELECT\""), "expected mode=SELECT in:\n" + reportJson);
        assertTrue(reportJson.contains("\"selectedCount\":2"), "expected selectedCount=2 in:\n" + reportJson);
        assertTrue(reportJson.contains("\"totalCount\":3"), "expected totalCount=3 in:\n" + reportJson);
    }
}
