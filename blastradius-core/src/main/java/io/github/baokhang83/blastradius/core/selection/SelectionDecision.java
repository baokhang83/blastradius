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
 *                             {@code reason == DEPENDENCY_MATCH || reason == DIRECT_INVOCATION_REFERENCE}
 * @param directInvocationSourceClass the executed class that declared the direct invocation,
 *                                    only present for {@code DIRECT_INVOCATION_REFERENCE}
 */
public record SelectionDecision(
        TestIdentity test, boolean selected, SelectionReason reason, String matchedChangedClass,
        String directInvocationSourceClass) {

    /** Preserves callers that do not carry a direct-invocation source. */
    public SelectionDecision(TestIdentity test, boolean selected, SelectionReason reason, String matchedChangedClass) {
        this(test, selected, reason, matchedChangedClass, null);
    }

    public SelectionDecision {
        Objects.requireNonNull(test, "test");
        Objects.requireNonNull(reason, "reason");
        if (reason == SelectionReason.DEPENDENCY_MATCH || reason == SelectionReason.DIRECT_INVOCATION_REFERENCE) {
            Objects.requireNonNull(matchedChangedClass, "matchedChangedClass required for DEPENDENCY_MATCH");
        }
        if (reason == SelectionReason.DIRECT_INVOCATION_REFERENCE) {
            Objects.requireNonNull(directInvocationSourceClass,
                    "directInvocationSourceClass required for DIRECT_INVOCATION_REFERENCE");
        }
        boolean expectedSelected = reason != SelectionReason.NO_MATCH;
        if (selected != expectedSelected) {
            throw new IllegalArgumentException("selected must be true iff reason is not NO_MATCH");
        }
    }

    public static SelectionDecision dependencyMatch(TestIdentity test, String matchedChangedClass) {
        return new SelectionDecision(test, true, SelectionReason.DEPENDENCY_MATCH, matchedChangedClass, null);
    }

    public static SelectionDecision directInvocationReference(
            TestIdentity test, String sourceClass, String matchedChangedClass) {
        return new SelectionDecision(test, true, SelectionReason.DIRECT_INVOCATION_REFERENCE, matchedChangedClass, sourceClass);
    }

    public static SelectionDecision fallback(TestIdentity test) {
        return new SelectionDecision(test, true, SelectionReason.FALLBACK_NON_SOURCE_CHANGE, null, null);
    }

    public static SelectionDecision fallbackAmbientDependency(TestIdentity test) {
        return new SelectionDecision(test, true, SelectionReason.FALLBACK_AMBIENT_DEPENDENCY, null, null);
    }

    public static SelectionDecision fallbackNonSourceDependentModule(TestIdentity test) {
        return new SelectionDecision(test, true, SelectionReason.FALLBACK_NON_SOURCE_DEPENDENT_MODULE, null, null);
    }

    public static SelectionDecision newOrModifiedTest(TestIdentity test) {
        return new SelectionDecision(test, true, SelectionReason.NEW_OR_MODIFIED_TEST, null, null);
    }

    public static SelectionDecision noMatch(TestIdentity test) {
        return new SelectionDecision(test, false, SelectionReason.NO_MATCH, null, null);
    }
}
