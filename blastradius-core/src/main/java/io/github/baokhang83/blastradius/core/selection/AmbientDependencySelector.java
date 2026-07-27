package io.github.baokhang83.blastradius.core.selection;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.Collections;
import java.util.Set;

/**
 * A distinct, deliberately conservative fallback rule alongside {@link FallbackSelector}: a
 * changed class that was loaded before any test's tracking window opened in some fork (see
 * {@code DependencyTrackingAgent#ambientDependencies()}) has no trustworthy per-test
 * dependency data — no test can be soundly ruled out for it, so every test is selected
 * instead of risking a silent {@link SelectionReason#NO_MATCH}.
 */
public final class AmbientDependencySelector {

    public boolean shouldFallback(Set<String> changedClassNames, Set<String> ambientDependencies) {
        return !Collections.disjoint(changedClassNames, ambientDependencies);
    }

    public SelectionDecision select(TestIdentity test) {
        return SelectionDecision.fallbackAmbientDependency(test);
    }
}
