package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests (tasks.md T049): malformed plugin configuration must fail the build with
 * a clear, distinct message — not a raw stack trace, and not a misleadingly "safe"
 * FALLBACK/TRACK outcome that silently ran the full suite anyway.
 */
class InvalidConfigurationTest {

    @BeforeAll
    static void installThisPluginOnce() throws Exception {
        EndToEndTestSupport.installThisPluginOnce();
    }

    @Test
    void missingBaseRefFailsWithClearMessage(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = EndToEndTestSupport.seedFooBarFixture(projectDir);
        fixture.commit("initial");
        fixture.addBuildPlugin(null, EndToEndTestSupport.pluginXmlMissingBaseRef());

        String output = EndToEndTestSupport.runMvnTest(projectDir);

        assertTrue(output.contains("BUILD FAILURE"), "expected the build to fail:\n" + output);
        assertTrue(output.toLowerCase().contains("baseref"),
                "expected the failure to mention the missing baseRef:\n" + output);
    }

    @Test
    void indexPathOutsideProjectDirectoryFailsWithClearMessage(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = EndToEndTestSupport.seedFooBarFixture(projectDir);
        String anchorCommit = fixture.commit("initial");
        fixture.addBuildPlugin(null, EndToEndTestSupport.pluginXml(anchorCommit, "../outside/index.json"));

        String output = EndToEndTestSupport.runMvnTest(projectDir);

        assertTrue(output.contains("BUILD FAILURE"), "expected the build to fail:\n" + output);
        assertTrue(output.toLowerCase().contains("indexpath"),
                "expected the failure to mention the invalid indexPath:\n" + output);
    }

    @Test
    void nonGitTargetProjectFailsWithClearMessage(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = EndToEndTestSupport.seedFooBarFixture(projectDir);
        String anchorCommit = fixture.commit("initial");
        fixture.addBuildPlugin(null, EndToEndTestSupport.pluginXml(anchorCommit));
        EndToEndTestSupport.removeGitRepository(projectDir);

        String output = EndToEndTestSupport.runMvnTest(projectDir);

        assertTrue(output.contains("BUILD FAILURE"), "expected the build to fail:\n" + output);
        assertTrue(output.toLowerCase().contains("git"),
                "expected the failure to mention the missing git repository:\n" + output);
    }
}
