package io.github.baokhang83.blastradius.validator.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import io.github.baokhang83.blastradius.core.tracking.DependencyRecordReader;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenBuildRunnerTest {

    private final MavenBuildRunner runner = new MavenBuildRunner();

    @Test
    void runningAPassingProjectReturnsExitCodeZero(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.writeClass("com.example.Foo",
                "package com.example; public class Foo { public int value() { return 1; } }");
        fixture.writeTest("com.example.FooTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class FooTest {
                    @Test
                    void passes() {
                        assertEquals(1, new Foo().value());
                    }
                }
                """);
        fixture.commit("initial");

        BuildResult result = runner.run(projectDir, null, null);

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("BUILD SUCCESS"));
    }

    @Test
    void runningAFailingTestReturnsNonZeroExitCode(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.writeTest("com.example.BrokenTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.fail;
                class BrokenTest {
                    @Test
                    void deliberatelyFails() {
                        fail("intentional failure");
                    }
                }
                """);
        fixture.commit("initial");

        BuildResult result = runner.run(projectDir, null, null);

        assertTrue(result.exitCode() != 0);
    }

    @Test
    void agentJarIsAttachedViaArgLineWhenProvided(@TempDir Path projectDir, @TempDir Path outDir) throws IOException {
        Path agentJar = findOwnAgentJar();
        Path recordFile = outDir.resolve("deps.json");

        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.addSystemDependency(null, agentJar);
        fixture.writeClass("com.example.Foo",
                "package com.example; public class Foo { public int value() { return 1; } }");
        fixture.writeTest("com.example.FooTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class FooTest {
                    @Test
                    void passes() {
                        assertEquals(1, new Foo().value());
                    }
                }
                """);
        fixture.commit("initial");

        BuildResult result = runner.run(projectDir, agentJar, recordFile);

        assertEquals(0, result.exitCode());
        assertTrue(!new DependencyRecordReader().readAll(recordFile).isEmpty(),
                "agent should have written its dependency record");
    }

    @Test
    void runSingleTestExecutesOnlyTheNamedTest(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.writeTest("com.example.MixedTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertTrue;
                import static org.junit.jupiter.api.Assertions.fail;
                class MixedTest {
                    @Test
                    void passes() {
                        assertTrue(true);
                    }
                    @Test
                    void wouldFailIfRun() {
                        fail("should not have been run");
                    }
                }
                """);
        fixture.commit("initial");

        BuildResult result = runner.runSingleTest(projectDir, new TestIdentity("com.example.MixedTest", "passes"));

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Tests run: 1"),
                "expected exactly one test to run:\n" + result.output());
    }

    private static Path findOwnAgentJar() throws IOException {
        Path targetDir = Path.of("target");
        try (var stream = Files.list(targetDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().matches("blastradius-validator-.*\\.jar"))
                    .filter(p -> !p.getFileName().toString().contains("sources"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("agent jar not found in target/"));
        }
    }
}
