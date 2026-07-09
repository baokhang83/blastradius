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
 * Integration test (tasks.md T032, US2/FR-006): a brand-new test class with no tracked
 * baseline must always be selected, regardless of the persisted index's contents — even
 * though adding it is, itself, the only changed file (a Java-source change, not a
 * non-source one, so this must be distinct from {@link NonSourceChangeFallbackTest}'s
 * fallback path — the new/modified-test rule, not the conservative fallback rule, is what
 * fires here).
 */
class NewTestAlwaysSelectedTest {

    @BeforeAll
    static void installThisPluginOnce() throws Exception {
        EndToEndTestSupport.installThisPluginOnce();
    }

    @Test
    void aBrandNewTestIsAlwaysSelected(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = EndToEndTestSupport.seedFooBarFixture(projectDir);
        fixture.writeClass("com.example.Baz",
                "package com.example; public class Baz { public int value() { return 3; } }");
        String anchorCommit = fixture.commit("initial");

        DependencyIndex index = EndToEndTestSupport.trackDependencies(projectDir, anchorCommit);
        new DependencyIndexWriter().write(projectDir.resolve(".blastradius/index.json"), index);

        // Only a brand-new test is added — no production class changes at all.
        fixture.writeTest("com.example.BazTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class BazTest {
                    @Test
                    void checksBaz() { assertEquals(3, new Baz().value()); }
                }
                """);
        fixture.commit("add BazTest");

        fixture.addBuildPlugin(null, EndToEndTestSupport.pluginXml(anchorCommit));

        String output = EndToEndTestSupport.runMvnTest(projectDir);

        assertTrue(output.contains("BUILD SUCCESS"), "expected the build to succeed:\n" + output);
        assertTrue(output.contains("Running com.example.BazTest"),
                "expected the brand-new test to always be selected:\n" + output);
        assertFalse(output.contains("Running com.example.FooTest"),
                "expected unrelated existing tests to still be skipped:\n" + output);
        assertFalse(output.contains("Running com.example.BarTest"),
                "expected unrelated existing tests to still be skipped:\n" + output);
    }
}
