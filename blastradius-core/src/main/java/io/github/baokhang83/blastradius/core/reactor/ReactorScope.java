package io.github.baokhang83.blastradius.core.reactor;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.FileKind;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The reactor's answer to "which tests can a NON_SOURCE change actually affect?" — the graph
 * ({@link ReactorModuleGraph}) plus the {@link TestModuleIndex}, computed once per commit pair
 * and handed to the {@code SelectionEngine} so it can scope the otherwise whole-suite fallback.
 *
 * <p>Soundness (Constitution &sect;III) is preserved by two escape hatches, both of which widen
 * back to the whole suite rather than risk a silent skip:
 * <ul>
 *   <li>{@link #isReactorWide(List)} — a root/parent-pom change, or any changed file attributable
 *       to no leaf module, affects everything.</li>
 *   <li>{@link #affects(TestIdentity)} returns {@code true} for any test whose own module can't be
 *       determined — never guess a test into a narrower scope.</li>
 * </ul>
 */
public final class ReactorScope {

    private final ReactorModuleGraph graph;
    private final TestModuleIndex testModuleIndex;
    private final Set<ModuleId> affectedModules;

    public ReactorScope(ReactorModuleGraph graph, TestModuleIndex testModuleIndex) {
        this.graph = graph;
        this.testModuleIndex = testModuleIndex;
        this.affectedModules = Set.of();
    }

    private ReactorScope(ReactorModuleGraph graph, TestModuleIndex testModuleIndex, Set<ModuleId> affectedModules) {
        this.graph = graph;
        this.testModuleIndex = testModuleIndex;
        this.affectedModules = affectedModules;
    }

    /**
     * True when the NON_SOURCE change set must select the whole suite because at least one
     * changed file is reactor-wide (root/parent pom, or unattributable to any leaf module).
     */
    public boolean isReactorWide(List<ChangedFile> changedFiles) {
        return nonSourcePaths(changedFiles).stream().anyMatch(graph::isReactorWide);
    }

    /**
     * Returns a scope carrying the set of modules affected by {@code changedFiles}: for each
     * NON_SOURCE changed file, its owning module plus every module that transitively depends
     * on it. Call {@link #isReactorWide(List)} first; this assumes no changed file is
     * reactor-wide.
     */
    public ReactorScope forChanges(List<ChangedFile> changedFiles) {
        Set<ModuleId> affected = new HashSet<>();
        for (String path : nonSourcePaths(changedFiles)) {
            graph.moduleOf(path).map(graph::dependentsOf).ifPresent(affected::addAll);
        }
        return new ReactorScope(graph, testModuleIndex, Set.copyOf(affected));
    }

    /**
     * Whether {@code test} lives in a module the current change set affects. A test whose
     * module can't be resolved is conservatively treated as affected.
     */
    public boolean affects(TestIdentity test) {
        Optional<ModuleId> module = testModuleIndex.moduleOf(test);
        return module.isEmpty() || affectedModules.contains(module.get());
    }

    private static List<String> nonSourcePaths(List<ChangedFile> changedFiles) {
        return changedFiles.stream()
                .filter(f -> f.kind() == FileKind.NON_SOURCE)
                .map(ChangedFile::path)
                .toList();
    }
}
