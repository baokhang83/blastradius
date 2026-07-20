package io.github.baokhang83.blastradius.core.selection;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.Set;

/** Selects a test when a changed class intersects its tracked dependencies (FR-002). */
public final class DependencyMatchSelector {

    public SelectionDecision select(TestIdentity test, Set<String> testDependencies, Set<String> changedClassNames) {
        for (String changedClassName : changedClassNames) {
            if (testDependencies.contains(changedClassName)
                    || testDependencies.stream().anyMatch(
                            dependency -> dependency.startsWith(changedClassName + "$"))) {
                return SelectionDecision.dependencyMatch(test, changedClassName);
            }
        }
        return SelectionDecision.noMatch(test);
    }
}
