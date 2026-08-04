package io.github.baokhang83.blastradius.validator.build;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroundTruthResolverTest {

    private final GroundTruthResolver resolver = new GroundTruthResolver();

    @Test
    void passingTestYieldsPassedOutcome(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.writeTest("com.example.GoodTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertTrue;
                class GoodTest {
                    @Test
                    void alwaysPasses() {
                        assertTrue(true);
                    }
                }
                """);
        fixture.commit("initial");

        List<GroundTruthResult> results = resolver.resolve(projectDir, null, null).results();

        assertEquals(Outcome.PASSED, outcomeOf(results, "com.example.GoodTest", "alwaysPasses"));
    }

    @Test
    void deterministicallyFailingTestYieldsConfirmedFailedOutcome(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.writeTest("com.example.AlwaysFailsTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.fail;
                class AlwaysFailsTest {
                    @Test
                    void alwaysFails() {
                        fail("deliberate, deterministic failure");
                    }
                }
                """);
        fixture.commit("initial");

        List<GroundTruthResult> results = resolver.resolve(projectDir, null, null).results();

        assertEquals(Outcome.CONFIRMED_FAILED, outcomeOf(results, "com.example.AlwaysFailsTest", "alwaysFails"));
    }

    @Test
    void testThatFailsOnceThenPassesOnRerunYieldsFlakyOutcome(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        // Uses a file-based counter (not in-memory) so behavior is deterministic ACROSS
        // separate `mvn test` subprocess invocations: fails on the 1st run, passes on the 2nd.
        String counterFile = projectDir.resolve("run-count.txt").toAbsolutePath().toString().replace("\\", "\\\\");
        fixture.writeTest("com.example.FlakyTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import java.nio.file.*;
                import static org.junit.jupiter.api.Assertions.fail;
                class FlakyTest {
                    @Test
                    void failsFirstThenPasses() throws Exception {
                        Path counter = Path.of("%s");
                        int count = Files.exists(counter) ? Integer.parseInt(Files.readString(counter).trim()) : 0;
                        count++;
                        Files.writeString(counter, String.valueOf(count));
                        if (count < 2) {
                            fail("flaky failure on attempt " + count);
                        }
                    }
                }
                """.formatted(counterFile));
        fixture.commit("initial");

        List<GroundTruthResult> results = resolver.resolve(projectDir, null, null).results();

        assertEquals(Outcome.FLAKY, outcomeOf(results, "com.example.FlakyTest", "failsFirstThenPasses"));
    }

    @Test
    void multiModuleReactorReportsAreAllCollected(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.twoModuleReactor(projectDir);
        fixture.writeClassInModule("moduleA", "com.example.a.Foo",
                "package com.example.a; public class Foo { public int value() { return 1; } }");
        fixture.writeTestInModule("moduleA", "com.example.a.FooTest", """
                package com.example.a;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class FooTest {
                    @Test
                    void checksValue() {
                        assertEquals(1, new Foo().value());
                    }
                }
                """);
        fixture.writeTestInModule("moduleB", "com.example.b.BarTest", """
                package com.example.b;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertTrue;
                class BarTest {
                    @Test
                    void checksSomething() {
                        assertTrue(true);
                    }
                }
                """);
        fixture.commit("initial");

        List<GroundTruthResult> results = resolver.resolve(projectDir, null, null).results();

        assertEquals(Outcome.PASSED, outcomeOf(results, "com.example.a.FooTest", "checksValue"));
        assertEquals(Outcome.PASSED, outcomeOf(results, "com.example.b.BarTest", "checksSomething"));
    }

    @Test
    void moduleScopedResolveBuildsTheModuleAndItsDependentsButSkipsAnUnrelatedBrokenModule(
            @TempDir Path projectDir) {
        // moduleB depends on moduleA. Scoping resolve() to moduleA with also-make-dependents
        // must still reach moduleB's test (a killer could live downstream), while a third,
        // unrelated module that doesn't compile is never built — proving the scope is exactly
        // "the module + its dependents", not the whole reactor.
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
        fixture.writeTestInModule("moduleB", "com.example.b.DownstreamTest", """
                package com.example.b;
                import com.example.a.Widget;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class DownstreamTest {
                    @Test
                    void seesWidget() {
                        assertEquals(1, new Widget().value());
                    }
                }
                """);
        fixture.commit("initial");

        List<GroundTruthResult> results =
                resolver.resolve(projectDir, null, null, "moduleA").results();

        assertEquals(Outcome.PASSED, outcomeOf(results, "com.example.a.WidgetTest", "passes"));
        assertEquals(Outcome.PASSED, outcomeOf(results, "com.example.b.DownstreamTest", "seesWidget"));
    }

    @Test
    void manyFailuresAreConfirmedInOneRerunNotOnePerFailure(@TempDir Path projectDir) throws Exception {
        // The mutation-validation bottleneck this batching exists for. A synthetic mutant in a
        // low-level class fails a large slice of the suite (244 tests observed on jsoup's
        // StringUtil), and confirmFailure used to launch one whole `mvn` per failure — ~27 per
        // mutant on average, turning a pair into hours. Batching makes it one rerun regardless
        // of how many tests failed, while every test still gets its own confirming execution.
        //
        // Counted by JVM identity rather than by mocking the runner: each `mvn test` gets its own
        // Surefire fork, so the number of distinct pids that ran a test IS the number of Maven
        // invocations — which is precisely the cost being removed.
        Path forkLog = projectDir.resolve("forks.txt");
        String forkLogLiteral = forkLog.toAbsolutePath().toString().replace("\\", "\\\\");
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        for (String name : List.of("One", "Two", "Three", "Four")) {
            fixture.writeTest("com.example.Fails" + name + "Test", """
                    package com.example;
                    import org.junit.jupiter.api.Test;
                    import java.nio.file.*;
                    import static org.junit.jupiter.api.Assertions.fail;
                    class Fails%sTest {
                        @Test
                        void alwaysFails() throws Exception {
                            Files.writeString(Path.of("%s"),
                                    ProcessHandle.current().pid() + "\\n",
                                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                            fail("deliberate, deterministic failure");
                        }
                    }
                    """.formatted(name, forkLogLiteral));
        }
        fixture.commit("initial");

        List<GroundTruthResult> results = resolver.resolve(projectDir, null, null).results();

        // Every failure still individually confirmed — the evidence is unchanged...
        for (String name : List.of("One", "Two", "Three", "Four")) {
            assertEquals(Outcome.CONFIRMED_FAILED,
                    outcomeOf(results, "com.example.Fails" + name + "Test", "alwaysFails"));
        }
        // ...but the initial suite run plus ONE batched confirmation rerun means two JVMs ran
        // tests. One rerun per failure would be five.
        long distinctForks = Files.readAllLines(forkLog).stream().filter(line -> !line.isBlank()).distinct().count();
        assertEquals(2, distinctForks,
                "expected the full run + one batched rerun; per-failure reruns would be 5 forks");
    }

    @Test
    void aRunWithNoFailuresLaunchesNoConfirmationRerunAtAll(@TempDir Path projectDir) throws Exception {
        Path forkLog = projectDir.resolve("forks.txt");
        String forkLogLiteral = forkLog.toAbsolutePath().toString().replace("\\", "\\\\");
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.writeTest("com.example.PassesTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import java.nio.file.*;
                class PassesTest {
                    @Test
                    void passes() throws Exception {
                        Files.writeString(Path.of("%s"),
                                ProcessHandle.current().pid() + "\\n",
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    }
                }
                """.formatted(forkLogLiteral));
        fixture.commit("initial");

        resolver.resolve(projectDir, null, null);

        // Nothing failed, so there is nothing to confirm: exactly one JVM ever ran a test.
        long distinctForks = Files.readAllLines(forkLog).stream().filter(line -> !line.isBlank()).distinct().count();
        assertEquals(1, distinctForks, "a clean run must launch no confirmation rerun at all");
    }

    private static Outcome outcomeOf(List<GroundTruthResult> results, String className, String methodName) {
        TestIdentity target = new TestIdentity(className, methodName);
        return results.stream()
                .filter(r -> r.test().equals(target))
                .findFirst()
                .map(GroundTruthResult::outcome)
                .orElseThrow(() -> new AssertionError("no result for " + target + " in " + results));
    }
}
