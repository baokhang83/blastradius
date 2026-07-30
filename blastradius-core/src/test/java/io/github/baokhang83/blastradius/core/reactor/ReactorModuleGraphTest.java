package io.github.baokhang83.blastradius.core.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReactorModuleGraphTest {

    private final ReactorModuleGraphBuilder builder = new ReactorModuleGraphBuilder();

    @Test
    void moduleOfAttributesAPathToItsOwningModule(@TempDir Path tempDir) {
        FixtureProjectBuilder.twoModuleReactor(tempDir).commit("initial");

        ReactorModuleGraph graph = builder.fromRepoTree(tempDir);

        assertEquals(
                Optional.of("moduleA"),
                graph.moduleOf("moduleA/src/main/resources/app.yml").map(ModuleId::artifactId));
        assertEquals(
                Optional.of("moduleB"),
                graph.moduleOf("moduleB/pom.xml").map(ModuleId::artifactId));
    }

    @Test
    void moduleOfPrefersTheDeepestOwningModule(@TempDir Path tempDir) {
        FixtureProjectBuilder.twoModuleReactor(tempDir).commit("initial");

        ReactorModuleGraph graph = builder.fromRepoTree(tempDir);

        // A file under moduleA belongs to moduleA, not the reactor-root aggregator.
        Optional<ModuleId> owner = graph.moduleOf("moduleA/src/test/java/com/example/FooTest.java");
        assertEquals(Optional.of("moduleA"), owner.map(ModuleId::artifactId));
    }

    @Test
    void dependentsOfIncludesTheModuleItselfAndItsTransitiveDependents(@TempDir Path tempDir) {
        // moduleB depends on moduleA (see FixtureProjectBuilder.twoModuleReactor).
        FixtureProjectBuilder.twoModuleReactor(tempDir).commit("initial");

        ReactorModuleGraph graph = builder.fromRepoTree(tempDir);
        ModuleId moduleA = graph.moduleOf("moduleA/pom.xml").orElseThrow();
        ModuleId moduleB = graph.moduleOf("moduleB/pom.xml").orElseThrow();

        Set<ModuleId> dependentsOfA = graph.dependentsOf(moduleA);
        assertTrue(dependentsOfA.contains(moduleA), "a module is its own dependent");
        assertTrue(dependentsOfA.contains(moduleB), "moduleB depends on moduleA");

        // moduleB has no dependents, so only itself.
        assertEquals(Set.of(moduleB), graph.dependentsOf(moduleB));
    }

    @Test
    void reactorRootPomIsReactorWide(@TempDir Path tempDir) {
        FixtureProjectBuilder.twoModuleReactor(tempDir).commit("initial");

        ReactorModuleGraph graph = builder.fromRepoTree(tempDir);

        assertTrue(graph.isReactorWide("pom.xml"), "root aggregator pom affects every module");
        assertFalse(graph.isReactorWide("moduleA/pom.xml"), "a leaf module pom is not reactor-wide");
    }

    @Test
    void ignoresPomsCopiedIntoBuildOutputDirectories(@TempDir Path tempDir) {
        // A real reactor build leaves a copy of each module's pom.xml under target/ (e.g.
        // maven-archiver's pom.properties siblings, or a shaded/effective pom). The walk
        // prunes target/ and build/ wholesale, so such a copy must never register as a
        // phantom module that would corrupt the artifactId -> module map.
        FixtureProjectBuilder.twoModuleReactor(tempDir)
                .writeResource("moduleA/target/classes/META-INF/maven/fixture/moduleA/pom.xml", "<project/>")
                .writeResource("moduleB/build/tmp/pom.xml", "<project/>")
                .commit("initial with build output");

        ReactorModuleGraph graph = builder.fromRepoTree(tempDir);

        // The build-output pom under moduleA/target must not be attributed as its own module:
        // a file under moduleA still resolves to moduleA (the deepest real module), not to some
        // phantom rooted at moduleA/target/classes/...
        assertEquals(
                Optional.of("moduleA"),
                graph.moduleOf("moduleA/src/main/java/com/example/Foo.java").map(ModuleId::artifactId));
    }
}
