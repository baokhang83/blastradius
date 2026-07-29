package io.github.baokhang83.blastradius.core.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.FileKind;
import io.github.baokhang83.blastradius.core.reactor.ReactorModuleGraph;
import io.github.baokhang83.blastradius.core.reactor.ReactorModuleGraphBuilder;
import io.github.baokhang83.blastradius.core.reactor.ReactorScope;
import io.github.baokhang83.blastradius.core.reactor.TestModuleIndex;
import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reactor-scoped NON_SOURCE fallback: a resource change in module X selects X + its dependents only. */
class SelectionEngineReactorScopingTest {

    private final SelectionEngine engine = new SelectionEngine();
    private final ReactorModuleGraphBuilder graphBuilder = new ReactorModuleGraphBuilder();

    private static final TestIdentity TEST_A = new TestIdentity("com.example.a.ATest", "a");
    private static final TestIdentity TEST_B = new TestIdentity("com.example.b.BTest", "b");

    private ReactorScope scopeFor(Path repoRoot) {
        ReactorModuleGraph graph = graphBuilder.fromRepoTree(repoRoot);
        return new ReactorScope(graph, TestModuleIndex.fromRepoTree(repoRoot, graph));
    }

    private Path reactorWithTests(Path root) {
        FixtureProjectBuilder.twoModuleReactor(root)
                .writeTestInModule("moduleA", "com.example.a.ATest", "package com.example.a; class ATest {}")
                .writeTestInModule("moduleB", "com.example.b.BTest", "package com.example.b; class BTest {}")
                .commit("tests in both modules");
        return root;
    }

    private SelectionDecision decisionFor(List<SelectionDecision> decisions, TestIdentity test) {
        return decisions.stream().filter(d -> d.test().equals(test)).findFirst().orElseThrow();
    }

    @Test
    void nonSourceChangeInALeafSelectsOnlyThatModuleAndItsDependents(@TempDir Path tempDir) {
        // moduleB depends on moduleA. A resource change in moduleA affects A and B.
        Path root = reactorWithTests(tempDir);
        List<ChangedFile> changed = List.of(
                new ChangedFile("moduleA/src/main/resources/app.yml", FileKind.NON_SOURCE, null));

        List<SelectionDecision> decisions = engine.selectAll(
                Set.of(TEST_A, TEST_B), Map.of(), Set.of(), changed, Set.of(), scopeFor(root));

        assertTrue(decisionFor(decisions, TEST_A).selected());
        assertTrue(decisionFor(decisions, TEST_B).selected(), "moduleB depends on moduleA");
        assertEquals(
                SelectionReason.FALLBACK_NON_SOURCE_DEPENDENT_MODULE, decisionFor(decisions, TEST_A).reason());
    }

    @Test
    void nonSourceChangeInADependedUponLeafDoesNotSelectIndependentModules(@TempDir Path tempDir) {
        // A resource change in moduleB: moduleA does NOT depend on moduleB, so ATest is spared.
        Path root = reactorWithTests(tempDir);
        List<ChangedFile> changed = List.of(
                new ChangedFile("moduleB/src/main/resources/app.yml", FileKind.NON_SOURCE, null));

        List<SelectionDecision> decisions = engine.selectAll(
                Set.of(TEST_A, TEST_B), Map.of(), Set.of(), changed, Set.of(), scopeFor(root));

        assertTrue(decisionFor(decisions, TEST_B).selected());
        assertEquals(
                SelectionReason.FALLBACK_NON_SOURCE_DEPENDENT_MODULE, decisionFor(decisions, TEST_B).reason());
        assertTrue(!decisionFor(decisions, TEST_A).selected(), "moduleA does not depend on moduleB");
        assertEquals(SelectionReason.NO_MATCH, decisionFor(decisions, TEST_A).reason());
    }

    @Test
    void rootPomChangeStillSelectsEveryTest(@TempDir Path tempDir) {
        Path root = reactorWithTests(tempDir);
        List<ChangedFile> changed = List.of(new ChangedFile("pom.xml", FileKind.NON_SOURCE, null));

        List<SelectionDecision> decisions = engine.selectAll(
                Set.of(TEST_A, TEST_B), Map.of(), Set.of(), changed, Set.of(), scopeFor(root));

        assertTrue(decisions.stream().allMatch(SelectionDecision::selected));
        assertTrue(decisions.stream()
                .allMatch(d -> d.reason() == SelectionReason.FALLBACK_NON_SOURCE_CHANGE));
    }

    @Test
    void nullReactorScopePreservesWholeSuiteFallback(@TempDir Path tempDir) {
        List<ChangedFile> changed = List.of(
                new ChangedFile("moduleB/src/main/resources/app.yml", FileKind.NON_SOURCE, null));

        List<SelectionDecision> decisions = engine.selectAll(
                Set.of(TEST_A, TEST_B), Map.of(), Set.of(), changed, Set.of(), null);

        assertTrue(decisions.stream().allMatch(SelectionDecision::selected));
        assertTrue(decisions.stream()
                .allMatch(d -> d.reason() == SelectionReason.FALLBACK_NON_SOURCE_CHANGE));
    }
}
