package io.github.baokhang83.blastradius.validator.build;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertTrue(!new DependencyRecordReader().readAll(recordFile).tests().isEmpty(),
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

    @Test
    void runSingleTestFindsAndPassesAMatchInTheCorrectModuleOfAReactorWhereOtherModulesHaveUnrelatedTests(
            @TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.twoModuleReactor(projectDir);
        fixture.writeClassInModule("moduleA", "com.example.a.Widget",
                "package com.example.a; public class Widget { public int value() { return 1; } }");
        fixture.writeTestInModule("moduleA", "com.example.a.WidgetTest", """
                package com.example.a;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class WidgetTest {
                    @Test
                    void passes() {
                        assertEquals(1, new Widget().value());
                    }
                }
                """);
        fixture.writeClassInModule("moduleB", "com.example.b.Gadget",
                "package com.example.b; public class Gadget { public int value() { return 2; } }");
        fixture.writeTestInModule("moduleB", "com.example.b.GadgetTest", """
                package com.example.b;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class GadgetTest {
                    @Test
                    void passes() {
                        assertEquals(2, new Gadget().value());
                    }
                }
                """);
        fixture.commit("initial");

        // moduleA has test sources but none named GadgetTest — without failIfNoTests=false
        // this aborts the whole reactor at moduleA before moduleB (the test's real home)
        // is ever reached.
        BuildResult result = runner.runSingleTest(projectDir, new TestIdentity("com.example.b.GadgetTest", "passes"));

        assertEquals(0, result.exitCode(), "expected the reactor build to reach moduleB and pass:\n" + result.output());
        assertTrue(result.output().contains("Tests run: 1"),
                "expected exactly one test to run:\n" + result.output());
    }

    @Test
    void moduleScopedRerunSkipsAnUnrelatedBrokenModule(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.twoModuleReactor(projectDir);
        fixture.writeClassInModule("moduleA", "com.example.a.Widget",
                "package com.example.a; public class Widget { public int value() { return 1; } }");
        fixture.writeTestInModule("moduleA", "com.example.a.WidgetTest", """
                package com.example.a;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class WidgetTest {
                    @Test
                    void passes() {
                        assertEquals(1, new Widget().value());
                    }
                }
                """);
        // moduleB depends on moduleA (not the reverse), so scoping the rerun to moduleA
        // via -pl/-am never needs to touch moduleB — even though moduleB doesn't compile.
        fixture.writeClassInModule("moduleB", "com.example.b.Broken", "package com.example.b; public class Broken {");
        fixture.commit("initial");

        Path moduleADir = projectDir.resolve("moduleA");
        BuildResult result = runner.runSingleTest(
                projectDir, new TestIdentity("com.example.a.WidgetTest", "passes"), moduleADir);

        assertEquals(0, result.exitCode(),
                "module-scoped rerun should succeed despite moduleB's unrelated compile failure:\n"
                        + result.output());
        assertTrue(result.output().contains("Tests run: 1"),
                "expected exactly one test to run:\n" + result.output());
    }

    @Test
    void moduleScopedSingleTestAddsPlAndAlsoMake() {
        assertArrayEquals(
                new String[] {
                    "mvn", "-B", "--no-transfer-progress", "clean", "test",
                    "-Dtest=com.example.FooTest#passes",
                    "-DfailIfNoTests=false", "-Dsurefire.failIfNoSpecifiedTests=false",
                    "-pl", "moduleB", "-am"
                },
                new MavenBuildRunner().command("com.example.FooTest#passes", "moduleB"));
    }

    @Test
    void nullModulePathOmitsPlAndAlsoMake() {
        assertArrayEquals(
                new String[] {
                    "mvn", "-B", "--no-transfer-progress", "clean", "test",
                    "-Dtest=com.example.FooTest#passes",
                    "-DfailIfNoTests=false", "-Dsurefire.failIfNoSpecifiedTests=false"
                },
                new MavenBuildRunner().command("com.example.FooTest#passes", null));
    }

    @Test
    void cleanFalseOmitsTheCleanGoal() {
        assertArrayEquals(
                new String[] {
                    "mvn", "-B", "--no-transfer-progress", "test",
                    "-Dtest=com.example.FooTest#passes",
                    "-DfailIfNoTests=false", "-Dsurefire.failIfNoSpecifiedTests=false",
                    "-pl", "moduleB", "-am"
                },
                new MavenBuildRunner().command("com.example.FooTest#passes", "moduleB", false));
    }

    @Test
    void confirmFailureRerunSecondCallSucceedsWithoutRecleaningTheFirstCallsModule(@TempDir Path projectDir) {
        // Proves runSingleTest's clean=false rerun is safe on an already-built checkout:
        // two back-to-back confirmFailure-style reruns of different tests in the same
        // module, neither preceded by a fresh clean, both still see correct, isolated
        // results — the scenario --fast-ground-truth's N+1 confirmation loop relies on.
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
        fixture.writeTest("com.example.BarTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class BarTest {
                    @Test
                    void alsoPasses() {
                        assertEquals(1, new Foo().value());
                    }
                }
                """);
        fixture.commit("initial");

        BuildResult initial = runner.run(projectDir, null, null);
        assertEquals(0, initial.exitCode(), "initial clean build should succeed:\n" + initial.output());

        BuildResult first = runner.runSingleTest(projectDir, new TestIdentity("com.example.FooTest", "passes"));
        BuildResult second = runner.runSingleTest(projectDir, new TestIdentity("com.example.BarTest", "alsoPasses"));

        assertEquals(0, first.exitCode(), "first no-clean rerun should succeed:\n" + first.output());
        assertEquals(0, second.exitCode(), "second no-clean rerun should succeed:\n" + second.output());
        assertTrue(second.output().contains("Tests run: 1"),
                "expected exactly one test to run on the second rerun:\n" + second.output());
    }

    @Test
    void noParallelThreadsOmitsTheTFlag() {
        assertArrayEquals(
                new String[] {"mvn", "-B", "--no-transfer-progress", "clean", "test"},
                new MavenBuildRunner().command(null));
    }

    @Test
    void parallelThreadsAddsTheTFlagToTheMavenCommand() {
        assertArrayEquals(
                new String[] {"mvn", "-B", "--no-transfer-progress", "clean", "test", "-T", "4"},
                new MavenBuildRunner(4).command(null));
    }

    @Test
    void nonPositiveParallelThreadsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MavenBuildRunner(0));
        assertThrows(IllegalArgumentException.class, () -> new MavenBuildRunner(-1));
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
