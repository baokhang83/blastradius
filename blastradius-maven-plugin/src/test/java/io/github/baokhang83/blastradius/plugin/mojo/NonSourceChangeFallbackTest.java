package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test (tasks.md T031, US2/FR-005): a change touching only a non-source file
 * (a resource) must trigger {@code blastradius-core}'s already-proven conservative
 * {@code FallbackSelector} rule live — every test runs, not just the ones whose tracked
 * dependencies happen to intersect the (nonexistent) changed Java classes.
 */
class NonSourceChangeFallbackTest {

    @BeforeAll
    static void installThisPluginOnce() throws Exception {
        EndToEndTestSupport.installThisPluginOnce();
    }

    @Test
    void aNonSourceChangeRunsEveryTest(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = EndToEndTestSupport.seedFooBarFixture(projectDir);
        fixture.writeResource("src/main/resources/config.properties", "greeting=hello\n");
        String anchorCommit = fixture.commit("initial");

        DependencyIndex index = EndToEndTestSupport.trackDependencies(projectDir, anchorCommit);
        EndToEndTestSupport.writeIndex(projectDir, index);

        // A change touching only a non-source resource — no Java class changed at all.
        fixture.writeResource("src/main/resources/config.properties", "greeting=hi\n");
        fixture.commit("change resource");

        fixture.addBuildPlugin(null, EndToEndTestSupport.pluginXml(anchorCommit));

        String output = EndToEndTestSupport.runMvnTest(projectDir);

        assertTrue(output.contains("BUILD SUCCESS"), "expected the build to succeed:\n" + output);
        assertTrue(output.contains("Running com.example.FooTest"),
                "expected the fallback rule to run every test, including FooTest:\n" + output);
        assertTrue(output.contains("Running com.example.BarTest"),
                "expected the fallback rule to run every test, including BarTest:\n" + output);
    }
}
