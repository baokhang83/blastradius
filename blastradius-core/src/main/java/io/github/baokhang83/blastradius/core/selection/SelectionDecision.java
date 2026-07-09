package io.github.baokhang83.blastradius.core.selection;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.Objects;

/**
 * For one test within one commit pair: whether it was selected, and why (Explainability,
 * Constitution Principle VI).
 *
 * @param test                 the test this decision concerns
 * @param selected             whether it was selected
 * @param reason               the reason (see {@link SelectionReason})
 * @param matchedChangedClass  the specific changed class responsible, only present when
 *                             {@code reason == DEPENDENCY_MATCH}
 */
public record SelectionDecision(
        TestIdentity test, boolean selected, SelectionReason reason, String matchedChangedClass) {

    public SelectionDecision {
        Objects.requireNonNull(test, "test");
        Objects.requireNonNull(reason, "reason");
        if (reason == SelectionReason.DEPENDENCY_MATCH) {
            Objects.requireNonNull(matchedChangedClass, "matchedChangedClass required for DEPENDENCY_MATCH");
        }
        boolean expectedSelected = reason != SelectionReason.NO_MATCH;
        if (selected != expectedSelected) {
            throw new IllegalArgumentException("selected must be true iff reason is not NO_MATCH");
        }
    }

    public static SelectionDecision dependencyMatch(TestIdentity test, String matchedChangedClass) {
        return new SelectionDecision(test, true, SelectionReason.DEPENDENCY_MATCH, matchedChangedClass);
    }

    public static SelectionDecision fallback(TestIdentity test) {
        return new SelectionDecision(test, true, SelectionReason.FALLBACK_NON_SOURCE_CHANGE, null);
    }

    public static SelectionDecision newOrModifiedTest(TestIdentity test) {
        return new SelectionDecision(test, true, SelectionReason.NEW_OR_MODIFIED_TEST, null);
    }

    public static SelectionDecision noMatch(TestIdentity test) {
        return new SelectionDecision(test, false, SelectionReason.NO_MATCH, null);
    }
}
