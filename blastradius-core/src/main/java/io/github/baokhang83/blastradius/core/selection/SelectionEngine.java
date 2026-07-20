package io.github.baokhang83.blastradius.core.selection;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
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
    private final NewOrModifiedTestSelector newOrModifiedTestSelector = new NewOrModifiedTestSelector();
    private final DependencyMatchSelector dependencyMatchSelector = new DependencyMatchSelector();

    /**
     * @param allTests           every test in the suite
     * @param testDependencies   each test's previously-tracked dependency class names
     * @param newOrModifiedTests tests with no prior baseline or whose own file changed
     * @param changedFiles       this commit pair's changed files
     */
    public List<SelectionDecision> selectAll(
            Set<TestIdentity> allTests,
            Map<TestIdentity, Set<String>> testDependencies,
            Set<TestIdentity> newOrModifiedTests,
            List<ChangedFile> changedFiles) {

        if (fallbackSelector.shouldFallback(changedFiles)) {
            return allTests.stream().map(fallbackSelector::select).toList();
        }

        Set<String> changedClassNames = changedFiles.stream()
                .flatMap(file -> file.candidateClassNames().stream())
                .collect(Collectors.toUnmodifiableSet());

        List<SelectionDecision> decisions = new ArrayList<>();
        for (TestIdentity test : allTests) {
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
