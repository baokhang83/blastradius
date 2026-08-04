package io.github.baokhang83.blastradius.validator.build;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.process.MavenLauncher;
import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import io.github.baokhang83.blastradius.core.tracking.DependencyRecordReader;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.validator.git.CommitCheckout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
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
    void excludedFailingTestDoesNotRunInTheFullSuite(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.writeTest("com.example.StableTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                class StableTest { @Test void passes() {} }
                """);
        fixture.writeTest("com.example.FlakyTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.fail;
                class FlakyTest { @Test void failsWhenRun() { fail("known flaky test"); } }
                """);
        fixture.commit("initial");

        BuildResult result = new MavenBuildRunner(null, 5, false, false,
                SkippedTests.parse(List.of("com.example.FlakyTest"))).run(projectDir, null, null);

        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("Tests run: 1"), result.output());
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
                    MavenLauncher.resolve(), "-B", "--no-transfer-progress", "clean", "test",
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
                    MavenLauncher.resolve(), "-B", "--no-transfer-progress", "clean", "test",
                    "-Dtest=com.example.FooTest#passes",
                    "-DfailIfNoTests=false", "-Dsurefire.failIfNoSpecifiedTests=false"
                },
                new MavenBuildRunner().command("com.example.FooTest#passes", null));
    }

    @Test
    void aBatchOfTestsBecomesOneCommaSeparatedSelector() {
        // One mvn invocation for N failures instead of N invocations. Surefire treats a
        // comma-separated -Dtest= as "run each of these", and each still runs in the same
        // forked-JVM isolation it got when it was named alone — so the per-test verdict is
        // unchanged while the process count drops from N to 1.
        assertArrayEquals(
                new String[] {
                    MavenLauncher.resolve(), "-B", "--no-transfer-progress", "test",
                    "-Dtest=com.example.FooTest#passes,com.example.BarTest#fails",
                    "-DfailIfNoTests=false", "-Dsurefire.failIfNoSpecifiedTests=false",
                    "-pl", "moduleB", "-am"
                },
                new MavenBuildRunner().command(
                        "com.example.FooTest#passes,com.example.BarTest#fails", "moduleB", false));
    }

    @Test
    void batchedRerunRunsEveryNamedTestInOneInvocation(@TempDir Path projectDir) {
        // The behavioural half of the batching change: three failures confirmed by ONE mvn
        // call must each actually execute, or confirmFailure would read back no report and
        // misclassify a genuine failure as flaky.
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        for (String name : List.of("Alpha", "Beta", "Gamma")) {
            fixture.writeTest("com.example." + name + "Test", """
                    package com.example;
                    import org.junit.jupiter.api.Test;
                    import static org.junit.jupiter.api.Assertions.assertTrue;
                    class %sTest {
                        @Test
                        void runs() {
                            assertTrue(true);
                        }
                    }
                    """.formatted(name));
        }
        fixture.commit("initial");

        BuildResult result = runner.runTests(projectDir, List.of(
                new TestIdentity("com.example.AlphaTest", "runs"),
                new TestIdentity("com.example.BetaTest", "runs"),
                new TestIdentity("com.example.GammaTest", "runs")), null);

        assertEquals(0, result.exitCode(), "batched rerun should succeed:\n" + result.output());
        assertTrue(result.output().contains("Tests run: 3"),
                "all three named tests should run in the one invocation:\n" + result.output());
    }

    @Test
    void anEmptyBatchIsNotRunAtAllRatherThanBecomingAFullSuiteRun() {
        // Guards the dangerous degenerate case: an empty selector would mean "-Dtest=" absent,
        // i.e. run EVERYTHING. A batch with no tests must do nothing instead.
        assertThrows(IllegalArgumentException.class,
                () -> new MavenBuildRunner().runTests(Path.of("."), List.of(), null));
    }

    @Test
    void fullSuiteCommandCarriesExplicitNegativeTestSelectors() {
        SkippedTests skipped = SkippedTests.parse(List.of("org.app.FlakyTest,org.app2.Flaky2Test"));

        assertArrayEquals(
                new String[] {
                    MavenLauncher.resolve(), "-B", "--no-transfer-progress", "clean", "test",
                    "-Dtest=!org.app.FlakyTest,!org.app2.Flaky2Test",
                    "-DfailIfNoTests=false", "-Dsurefire.failIfNoSpecifiedTests=false"
                },
                new MavenBuildRunner(null, 5, false, false, skipped).command(null));
    }

    @Test
    void moduleScopedFullSuiteAddsPlAmAndAmd() {
        // A synthetic mutant changes one file in one module; a killing test can live in that
        // module OR in any module that depends on it — so the scoped full-suite build needs
        // -amd (also-make-dependents) on top of -am, or a legitimately-selected downstream
        // killing test would be built-out and misreported as a would-miss (§III).
        assertArrayEquals(
                new String[] {
                    MavenLauncher.resolve(), "-B", "--no-transfer-progress", "clean", "test",
                    "-pl", "moduleB", "-am", "-amd"
                },
                new MavenBuildRunner().command(null, "moduleB", true, true));
    }

    @Test
    void alsoMakeDependentsWithoutAModulePathAddsNoAmd() {
        // -amd is meaningless without -pl; a null module path means full reactor already.
        assertArrayEquals(
                new String[] {MavenLauncher.resolve(), "-B", "--no-transfer-progress", "clean", "test"},
                new MavenBuildRunner().command(null, null, true, true));
    }

    @Test
    void cleanFalseOmitsTheCleanGoal() {
        assertArrayEquals(
                new String[] {
                    MavenLauncher.resolve(), "-B", "--no-transfer-progress", "test",
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
                new String[] {MavenLauncher.resolve(), "-B", "--no-transfer-progress", "clean", "test"},
                new MavenBuildRunner().command(null));
    }

    @Test
    void parallelThreadsAddsTheTFlagToTheMavenCommand() {
        assertArrayEquals(
                new String[] {MavenLauncher.resolve(), "-B", "--no-transfer-progress", "clean", "test", "-T", "4"},
                new MavenBuildRunner(4).command(null));
    }

    @Test
    void nonPositiveParallelThreadsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MavenBuildRunner(0));
        assertThrows(IllegalArgumentException.class, () -> new MavenBuildRunner(-1));
    }

    @Test
    void nonPositiveTimeoutIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MavenBuildRunner(null, 0));
        assertThrows(IllegalArgumentException.class, () -> new MavenBuildRunner(2, -5));
    }

    @Test
    void skipBuildExtrasAppendsCoverageLintAndResourceSkipsWithoutTouchingTestSelection() {
        // The skips must not include -DskipTests or any -pl/-am scoping: those would change which
        // tests run or whether they pass, corrupting the ground truth (§III). Only build-quality
        // plugins that don't decide test outcomes are switched off.
        assertArrayEquals(
                new String[] {
                    MavenLauncher.resolve(), "-B", "--no-transfer-progress", "clean", "test",
                    "-Djacoco.skip=true", "-Dcheckstyle.skip=true", "-Drat.skip=true",
                    "-DskipRemoteResources=true"
                },
                new MavenBuildRunner(null, 5, false, true).command(null));
    }

    @Test
    void skipBuildExtrasDefaultsOffSoUnconfiguredRunnersBuildExactlyAsBefore() {
        assertArrayEquals(
                new String[] {MavenLauncher.resolve(), "-B", "--no-transfer-progress", "clean", "test"},
                new MavenBuildRunner(null, 5, false, false).command(null));
    }

    @Test
    void isolatedRepoAppendsAClonePrivateMavenRepoLocalDerivedFromTheWorkingCopy(@TempDir Path projectDir) {
        MavenBuildRunner isolated = new MavenBuildRunner(null, 5, true);
        String[] base = isolated.command(null);

        String[] augmented = isolated.withIsolatedRepo(base, projectDir);

        Path expectedRepo = CommitCheckout.isolatedMavenRepoFor(projectDir);
        String[] expected = Arrays.copyOf(base, base.length + 1);
        expected[base.length] = "-Dmaven.repo.local=" + expectedRepo;
        assertArrayEquals(expected, augmented,
                "an isolated runner must append exactly one -Dmaven.repo.local pointing at the clone's private repo");
    }

    @Test
    void defaultRunnerLeavesTheCommandUntouchedSoTheSharedM2AndEveryExistingCallerAreUnaffected(
            @TempDir Path projectDir) {
        MavenBuildRunner shared = new MavenBuildRunner();
        String[] base = shared.command(null);

        assertArrayEquals(base, shared.withIsolatedRepo(base, projectDir),
                "a non-isolated runner must not touch the argument list command() composed");
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
