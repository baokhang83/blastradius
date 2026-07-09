package io.github.baokhang83.blastradius.plugin.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static Path findOwnAgentJar() throws IOException {
        Path targetDir = Path.of("..", "blastradius-core", "target");
        try (var stream = Files.list(targetDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().matches("blastradius-core-.*\\.jar"))
                    .filter(p -> !p.getFileName().toString().contains("sources"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "blastradius-core agent jar not found in ../blastradius-core/target — "
                                    + "expected the process-test-classes-phase jar execution to have produced it"));
        }
    }
}
