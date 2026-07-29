package io.github.baokhang83.blastradius.core.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestModuleIndexTest {

    private final ReactorModuleGraphBuilder graphBuilder = new ReactorModuleGraphBuilder();

    @Test
    void attributesAJavaTestClassToItsOwningModule(@TempDir Path tempDir) {
        FixtureProjectBuilder.twoModuleReactor(tempDir)
                .writeTestInModule("moduleA", "com.example.a.FooTest", "package com.example.a; class FooTest {}")
                .writeTestInModule("moduleB", "com.example.b.BarTest", "package com.example.b; class BarTest {}")
                .commit("tests in both modules");

        ReactorModuleGraph graph = graphBuilder.fromRepoTree(tempDir);
        TestModuleIndex index = TestModuleIndex.fromRepoTree(tempDir, graph);

        assertEquals(
                Optional.of("moduleA"),
                index.moduleOf(new TestIdentity("com.example.a.FooTest", "foo")).map(ModuleId::artifactId));
        assertEquals(
                Optional.of("moduleB"),
                index.moduleOf(new TestIdentity("com.example.b.BarTest", "bar")).map(ModuleId::artifactId));
    }

    @Test
    void attributesAKotlinTestClassToItsOwningModule(@TempDir Path tempDir) {
        FixtureProjectBuilder.twoModuleReactor(tempDir)
                .writeKotlinTestInModule("moduleA", "com.example.a.KotlinTest", "package com.example.a\nclass KotlinTest")
                .commit("kotlin test in moduleA");

        ReactorModuleGraph graph = graphBuilder.fromRepoTree(tempDir);
        TestModuleIndex index = TestModuleIndex.fromRepoTree(tempDir, graph);

        assertEquals(
                Optional.of("moduleA"),
                index.moduleOf(new TestIdentity("com.example.a.KotlinTest", null)).map(ModuleId::artifactId));
    }

    @Test
    void unknownTestClassResolvesToEmptySoCallerFallsBackToWholeSuite(@TempDir Path tempDir) {
        FixtureProjectBuilder.twoModuleReactor(tempDir).commit("no tests");

        ReactorModuleGraph graph = graphBuilder.fromRepoTree(tempDir);
        TestModuleIndex index = TestModuleIndex.fromRepoTree(tempDir, graph);

        assertTrue(index.moduleOf(new TestIdentity("com.example.Unknown", "t")).isEmpty());
    }
}
