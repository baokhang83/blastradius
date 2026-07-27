package io.github.baokhang83.blastradius.plugin.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackRunnerTest {

    private final TrackRunner trackRunner = new TrackRunner();

    @Test
    void tracksDependenciesFromARealSubprocessRun(@TempDir Path projectDir) throws Exception {
        Path agentJar = findOwnAgentJar();
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.addSystemDependency(null, agentJar);
        fixture.writeClass("com.example.Foo",
                "package com.example; public class Foo { public int value() { return 42; } }");
        fixture.writeTest("com.example.FooTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class FooTest {
                    @Test
                    void checksValue() {
                        assertEquals(42, new Foo().value());
                    }
                }
                """);
        String anchorCommit = fixture.commit("initial");

        DependencyIndex index = trackRunner.track(projectDir, agentJar, anchorCommit);

        assertEquals(anchorCommit, index.anchorCommit());
        assertTrue(index.builtAt() != null && !index.builtAt().isBlank());

        TestIdentity fooTest = new TestIdentity("com.example.FooTest", "checksValue");
        var byTest = index.testDependenciesByTest();
        assertTrue(byTest.containsKey(fooTest), "expected an entry for " + fooTest + " in " + byTest.keySet());
        assertTrue(byTest.get(fooTest).contains("com.example.Foo"),
                "FooTest's tracked dependencies must include com.example.Foo");
    }

    @Test
    void tracksDownstreamModuleTests(@TempDir Path projectDir) throws Exception {
        Path agentJar = findOwnAgentJar();
        FixtureProjectBuilder fixture = FixtureProjectBuilder.twoModuleReactor(projectDir);
        fixture.writeClassInModule("moduleA", "com.example.a.Foo",
                "package com.example.a; public class Foo { public int value() { return 42; } }");
        fixture.writeClassInModule("moduleB", "com.example.b.Consumer", """
                package com.example.b;
                import com.example.a.Foo;
                public class Consumer {
                    public int value() { return new Foo().value(); }
                }
                """);
        fixture.writeTestInModule("moduleB", "com.example.b.ConsumerTest", """
                package com.example.b;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class ConsumerTest {
                    @Test
                    void checksValue() { assertEquals(42, new Consumer().value()); }
                }
                """);
        String anchorCommit = fixture.commit("initial");

        DependencyIndex index = trackRunner.track(projectDir, agentJar, anchorCommit);

        TestIdentity consumerTest = new TestIdentity("com.example.b.ConsumerTest", "checksValue");
        assertTrue(index.testDependenciesByTest().containsKey(consumerTest),
                "expected downstream test in tracked index: " + index.testDependenciesByTest().keySet());
        assertTrue(index.testDependenciesByTest().get(consumerTest).contains("com.example.a.Foo"),
                "downstream test must retain its cross-module dependency");
    }

    @Test
    void rejectsAPartialIndexWhenTheTrackedBuildFails(@TempDir Path projectDir) throws Exception {
        Path agentJar = findOwnAgentJar();
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.addSystemDependency(null, agentJar);
        fixture.writeTest("com.example.FailingTest", """
                package com.example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.fail;
                class FailingTest {
                    @Test
                    void fails() { fail("intentional"); }
                }
                """);
        String anchorCommit = fixture.commit("initial");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, () -> trackRunner.track(projectDir, agentJar, anchorCommit));
        assertTrue(exception.getMessage().contains("intentional"),
                "expected the tracking subprocess output in the failure diagnostic: " + exception.getMessage());
    }

    @Test
    void classOfAnotherReactorModuleIsAttributedToTheTestUsingItRatherThanLeftAmbient(
            @TempDir Path projectDir) throws Exception {
        Path agentJar = findOwnAgentJar();
        FixtureProjectBuilder fixture = FixtureProjectBuilder.twoModuleReactor(projectDir);
        fixture.addSystemDependency("moduleB", agentJar);
        // moduleA packages before moduleB's tests run, so moduleB resolves it as a jar rather than
        // as a target/classes directory — the shape every real multi-module build has, and the one
        // that used to leave moduleA's classes permanently ambient.
        fixture.addBuildPlugin("moduleA", """
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-jar-plugin</artifactId>
                    <executions>
                        <execution>
                            <id>package-early</id>
                            <phase>process-test-classes</phase>
                            <goals><goal>jar</goal></goals>
                        </execution>
                    </executions>
                </plugin>
                """);
        fixture.writeClassInModule("moduleA", "com.example.a.Shared",
                "package com.example.a; public class Shared { public int value() { return 42; } }");
        // Naming Shared in a method signature makes JUnit's discovery pass load it before any test
        // starts, which is what puts it in the ambient snapshot in the first place.
        fixture.writeTestInModule("moduleB", "com.example.b.SharedUserTest", """
                package com.example.b;
                import com.example.a.Shared;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class SharedUserTest {
                    private Shared shared() {
                        return new Shared();
                    }

                    @Test
                    void usesShared() {
                        assertEquals(42, shared().value());
                    }
                }
                """);
        String anchorCommit = fixture.commit("initial");

        DependencyIndex index = trackRunner.track(projectDir, agentJar, anchorCommit);

        assertFalse(index.ambientDependencies().contains("com.example.a.Shared"),
                "a class built by this reactor must not stay ambient just because a downstream "
                        + "module loads it from a jar");
        assertTrue(index.testDependencies().stream()
                        .filter(entry -> entry.test().className().equals("com.example.b.SharedUserTest"))
                        .anyMatch(entry -> entry.dependsOnClasses().contains("com.example.a.Shared")),
                "the test that uses it must own it as a dependency");
    }

    private static Path findOwnAgentJar() throws IOException {
        Path targetDir = Path.of("..", "blastradius-core", "target");
        try (var stream = Files.list(targetDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().matches("blastradius-core-.*\\.jar"))
                    .filter(p -> p.getFileName().toString().contains("-agent"))
                    .filter(p -> !p.getFileName().toString().contains("sources"))
                    .filter(p -> !p.getFileName().toString().contains("tests"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "blastradius-core agent jar not found in ../blastradius-core/target — "
                                    + "expected the process-test-classes-phase jar execution to have produced it"));
        }
    }
}
