package io.github.baokhang83.blastradius.validator.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildFailureDetectionTest {

    private final MavenBuildRunner runner = new MavenBuildRunner();
    private final BuildFailureDetector detector = new BuildFailureDetector();

    @Test
    void compileErrorIsDetectedAsABuildFailure(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        // Deliberately invalid Java: never compiles, so Surefire never runs at all.
        fixture.writeClass("com.example.Broken", "package com.example; this is not valid java {{{");
        fixture.commit("broken commit");

        BuildResult result = runner.run(projectDir, null, null);

        assertTrue(detector.isBuildFailure(result, projectDir));
    }

    @Test
    void ordinaryTestFailureIsNotABuildFailure(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.writeTest("com.example.FailingTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.fail;
                class FailingTest {
                    @Test
                    void deliberatelyFails() {
                        fail("ordinary test failure, not a build failure");
                    }
                }
                """);
        fixture.commit("initial");

        BuildResult result = runner.run(projectDir, null, null);

        assertFalse(detector.isBuildFailure(result, projectDir));
    }

    @Test
    void successfulBuildIsNeverABuildFailure(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.writeTest("com.example.PassingTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertTrue;
                class PassingTest {
                    @Test
                    void passes() {
                        assertTrue(true);
                    }
                }
                """);
        fixture.commit("initial");

        BuildResult result = runner.run(projectDir, null, null);

        assertFalse(detector.isBuildFailure(result, projectDir));
    }
}
