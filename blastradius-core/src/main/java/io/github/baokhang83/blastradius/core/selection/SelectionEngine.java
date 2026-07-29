package io.github.baokhang83.blastradius.core.selection;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.reactor.ReactorScope;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Composes the three selection rules into one per-test {@link SelectionDecision} — the
 * deterministic core being validated (Constitution Principle IV).
 *
 * <p>Precedence: the fallback rule (FR-006), if triggered, short-circuits and selects
 * every test uniformly, since it doesn't depend on any individual test's dependencies.
 * Otherwise, the new/modified-test rule (FR-007) takes precedence per test over ordinary
 * dependency matching (FR-002).
 */
public final class SelectionEngine {

    private final FallbackSelector fallbackSelector = new FallbackSelector();
    private final AmbientDependencySelector ambientDependencySelector = new AmbientDependencySelector();
    private final NewOrModifiedTestSelector newOrModifiedTestSelector = new NewOrModifiedTestSelector();
    private final DependencyMatchSelector dependencyMatchSelector = new DependencyMatchSelector();

    /**
     * @param allTests           every test in the suite
     * @param testDependencies   each test's previously-tracked dependency class names
     * @param newOrModifiedTests tests with no prior baseline or whose own file changed
     * @param changedFiles       this commit pair's changed files
     * @param ambientDependencies classes loaded before any test's tracking window opened in
     *                            some fork — never soundly attributable to a specific test
     */
    public List<SelectionDecision> selectAll(
            Set<TestIdentity> allTests,
            Map<TestIdentity, Set<String>> testDependencies,
            Set<TestIdentity> newOrModifiedTests,
            List<ChangedFile> changedFiles,
            Set<String> ambientDependencies) {
        return selectAll(allTests, testDependencies, newOrModifiedTests, changedFiles, ambientDependencies, null);
    }

    /**
     * As {@link #selectAll(Set, Map, Set, List, Set)}, but with an optional {@code reactorScope}.
     * When present and the NON_SOURCE change is not reactor-wide, the fallback is scoped to the
     * changed modules and their transitive dependents instead of the whole suite. When
     * {@code null} (no reactor graph available), behavior is identical to the whole-suite fallback.
     */
    public List<SelectionDecision> selectAll(
            Set<TestIdentity> allTests,
            Map<TestIdentity, Set<String>> testDependencies,
            Set<TestIdentity> newOrModifiedTests,
            List<ChangedFile> changedFiles,
            Set<String> ambientDependencies,
            ReactorScope reactorScope) {

        boolean scopedFallback =
                fallbackSelector.shouldFallback(changedFiles)
                        && reactorScope != null
                        && !reactorScope.isReactorWide(changedFiles);

        if (fallbackSelector.shouldFallback(changedFiles) && !scopedFallback) {
            return allTests.stream().map(fallbackSelector::select).toList();
        }

        Set<String> changedClassNames = changedFiles.stream()
                .flatMap(file -> file.candidateClassNames().stream())
                .collect(Collectors.toUnmodifiableSet());

        // The ambient-dependency fallback is a whole-suite escape hatch and outranks reactor
        // scoping: a class loaded before any tracking window has no trustworthy per-test data,
        // so no module can be soundly ruled out for it.
        if (ambientDependencySelector.shouldFallback(changedClassNames, ambientDependencies)) {
            return allTests.stream().map(ambientDependencySelector::select).toList();
        }

        ReactorScope affectedScope = scopedFallback ? reactorScope.forChanges(changedFiles) : null;

        List<SelectionDecision> decisions = new ArrayList<>();
        for (TestIdentity test : allTests) {
            if (affectedScope != null && affectedScope.affects(test)) {
                decisions.add(SelectionDecision.fallbackNonSourceDependentModule(test));
                continue;
            }
            if (newOrModifiedTests.contains(test)) {
                decisions.add(newOrModifiedTestSelector.select(test));
                continue;
            }
            Set<String> dependencies = testDependencies.getOrDefault(test.baselineKey(), Set.of());
            decisions.add(dependencyMatchSelector.select(test, dependencies, changedClassNames));
        }
        return decisions;
    }
}
