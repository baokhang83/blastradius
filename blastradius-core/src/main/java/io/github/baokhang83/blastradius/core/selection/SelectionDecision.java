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
 *                             {@code reason == DEPENDENCY_MATCH || reason == DIRECT_INVOCATION_REFERENCE ||
 *                             reason == TRANSITIVE_DIRECT_INVOCATION_REFERENCE}
 * @param directInvocationSourceClass the executed class that declared the direct invocation,
 *                                    only present for a direct-invocation reason
 * @param directInvocationIntermediateClass the intermediate invocation target, only present for
 *                                          {@code TRANSITIVE_DIRECT_INVOCATION_REFERENCE}
 */
public record SelectionDecision(
        TestIdentity test, boolean selected, SelectionReason reason, String matchedChangedClass,
        String directInvocationSourceClass, String directInvocationIntermediateClass) {

    /** Preserves callers that do not carry a direct-invocation source. */
    public SelectionDecision(TestIdentity test, boolean selected, SelectionReason reason, String matchedChangedClass) {
        this(test, selected, reason, matchedChangedClass, null, null);
    }

    /** Preserves callers that carry a one-hop direct-invocation source. */
    public SelectionDecision(
            TestIdentity test, boolean selected, SelectionReason reason, String matchedChangedClass,
            String directInvocationSourceClass) {
        this(test, selected, reason, matchedChangedClass, directInvocationSourceClass, null);
    }

    public SelectionDecision {
        Objects.requireNonNull(test, "test");
        Objects.requireNonNull(reason, "reason");
        if (reason == SelectionReason.DEPENDENCY_MATCH
                || reason == SelectionReason.DIRECT_INVOCATION_REFERENCE
                || reason == SelectionReason.TRANSITIVE_DIRECT_INVOCATION_REFERENCE) {
            Objects.requireNonNull(matchedChangedClass, "matchedChangedClass required for DEPENDENCY_MATCH");
        }
        if (reason == SelectionReason.DIRECT_INVOCATION_REFERENCE
                || reason == SelectionReason.TRANSITIVE_DIRECT_INVOCATION_REFERENCE) {
            Objects.requireNonNull(directInvocationSourceClass,
                    "directInvocationSourceClass required for a direct-invocation reason");
        }
        if (reason == SelectionReason.DIRECT_INVOCATION_REFERENCE
                && directInvocationIntermediateClass != null) {
            throw new IllegalArgumentException(
                    "directInvocationIntermediateClass is only valid for a transitive direct-invocation reason");
        }
        if (reason == SelectionReason.TRANSITIVE_DIRECT_INVOCATION_REFERENCE) {
            Objects.requireNonNull(directInvocationIntermediateClass,
                    "directInvocationIntermediateClass required for TRANSITIVE_DIRECT_INVOCATION_REFERENCE");
        }
        boolean expectedSelected = reason != SelectionReason.NO_MATCH;
        if (selected != expectedSelected) {
            throw new IllegalArgumentException("selected must be true iff reason is not NO_MATCH");
        }
    }

    public static SelectionDecision dependencyMatch(TestIdentity test, String matchedChangedClass) {
        return new SelectionDecision(test, true, SelectionReason.DEPENDENCY_MATCH, matchedChangedClass, null, null);
    }

    public static SelectionDecision directInvocationReference(
            TestIdentity test, String sourceClass, String matchedChangedClass) {
        return new SelectionDecision(
                test, true, SelectionReason.DIRECT_INVOCATION_REFERENCE, matchedChangedClass, sourceClass, null);
    }

    public static SelectionDecision transitiveDirectInvocationReference(
            TestIdentity test, String sourceClass, String intermediateClass, String matchedChangedClass) {
        return new SelectionDecision(test, true, SelectionReason.TRANSITIVE_DIRECT_INVOCATION_REFERENCE,
                matchedChangedClass, sourceClass, intermediateClass);
    }

    public static SelectionDecision fallback(TestIdentity test) {
        return new SelectionDecision(test, true, SelectionReason.FALLBACK_NON_SOURCE_CHANGE, null, null, null);
    }

    public static SelectionDecision fallbackAmbientDependency(TestIdentity test) {
        return new SelectionDecision(test, true, SelectionReason.FALLBACK_AMBIENT_DEPENDENCY, null, null, null);
    }

    public static SelectionDecision fallbackNonSourceDependentModule(TestIdentity test) {
        return new SelectionDecision(test, true, SelectionReason.FALLBACK_NON_SOURCE_DEPENDENT_MODULE, null, null, null);
    }

    public static SelectionDecision newOrModifiedTest(TestIdentity test) {
        return new SelectionDecision(test, true, SelectionReason.NEW_OR_MODIFIED_TEST, null, null, null);
    }

    public static SelectionDecision noMatch(TestIdentity test) {
        return new SelectionDecision(test, false, SelectionReason.NO_MATCH, null, null, null);
    }
}
