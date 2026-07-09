package io.github.baokhang83.blastradius.validator.build;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
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

        List<GroundTruthResult> results = resolver.resolve(projectDir, null, null);

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

        List<GroundTruthResult> results = resolver.resolve(projectDir, null, null);

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

        List<GroundTruthResult> results = resolver.resolve(projectDir, null, null);

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

        List<GroundTruthResult> results = resolver.resolve(projectDir, null, null);

        assertEquals(Outcome.PASSED, outcomeOf(results, "com.example.a.FooTest", "checksValue"));
        assertEquals(Outcome.PASSED, outcomeOf(results, "com.example.b.BarTest", "checksSomething"));
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
