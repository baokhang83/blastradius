package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.index.CommitIndexKey;
import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackModeFailureFallbackTest {

    @BeforeAll
    static void installThisPluginOnce() throws Exception {
        EndToEndTestSupport.installThisPluginOnce();
    }

    @Test
    void aTrackingFailureDiscardsTheIndexAndLetsTheAmbientBuildRunAllTests(@TempDir Path projectDir)
            throws Exception {
        FixtureProjectBuilder fixture = EndToEndTestSupport.seedFooBarFixture(projectDir);
        fixture.writeTest("com.example.TrackChildOnlyFailureTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.fail;
                class TrackChildOnlyFailureTest {
                    @Test
                    void failsOnlyInsideTheTrackingSubprocess() {
                        if (Boolean.getBoolean("blastradius.trackChild")) {
                            fail("intentional tracking-child failure");
                        }
                    }
                }
                """);
        String anchorCommit = fixture.commit("initial");
        fixture.addBuildPlugin(null, EndToEndTestSupport.pluginXml(anchorCommit));

        String output = EndToEndTestSupport.runMvnTest(projectDir);

        assertTrue(output.contains("BUILD SUCCESS"), "expected the ambient build to succeed:\n" + output);
        assertTrue(output.contains("[blastradius] FALLBACK"),
                "expected failed tracking to report FALLBACK mode:\n" + output);
        Path indexFile = projectDir.resolve(CommitIndexKey.forCommit(".blastradius/index.json", anchorCommit));
        assertFalse(Files.exists(indexFile), "a failed tracking subprocess must not leave an index behind");
    }

    @Test
    void everyReactorModuleFallsBackWhenTheSingleTrackingRunFails(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.twoModuleReactor(projectDir);
        fixture.ignoreTargetDirectory();
        fixture.writeClassInModule("moduleA", "com.example.a.Foo",
                "package com.example.a; public class Foo { public int value() { return 1; } }");
        fixture.writeTestInModule("moduleA", "com.example.a.TrackChildOnlyFailureTest", """
                package com.example.a;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.fail;
                class TrackChildOnlyFailureTest {
                    @Test
                    void failsOnlyInsideTheTrackingSubprocess() {
                        if (Boolean.getBoolean("blastradius.trackChild")) {
                            fail("intentional tracking-child failure");
                        }
                    }
                }
                """);
        fixture.writeTestInModule("moduleB", "com.example.b.DownstreamTest", """
                package com.example.b;
                import org.junit.jupiter.api.Test;
                class DownstreamTest { @Test void passes() {} }
                """);
        String anchorCommit = fixture.commit("initial");
        String pluginXml = EndToEndTestSupport.pluginXml(anchorCommit);
        fixture.addBuildPlugin("moduleA", pluginXml);
        fixture.addBuildPlugin("moduleB", pluginXml);

        String output = EndToEndTestSupport.runMvnTest(projectDir);

        assertTrue(output.contains("BUILD SUCCESS"), "expected the reactor build to succeed:\n" + output);
        assertTrue(output.contains("Running com.example.b.DownstreamTest"),
                "expected the later module to run unfiltered after tracking failed:\n" + output);
        Path indexFile = projectDir.resolve(CommitIndexKey.forCommit(".blastradius/index.json", anchorCommit));
        assertFalse(Files.exists(indexFile), "a failed tracking subprocess must not leave an index behind");
    }
}
