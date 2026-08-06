package io.github.baokhang83.blastradius.core.selection;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.Map;
import java.util.Set;

/**
 * Selects a test when one of its dynamically-executed classes reaches a changed class in one or
 * two recorded static invocation edges.
 */
final class DirectInvocationReferenceSelector {

    SelectionDecision select(TestIdentity test, Map<String, Set<String>> directInvocationOwners,
            Map<String, Set<String>> invocationGraph, Set<String> changedClassNames) {
        for (Map.Entry<String, Set<String>> source : directInvocationOwners.entrySet()) {
            for (String changedClassName : changedClassNames) {
                if (referencesChangedClass(source.getValue(), changedClassName)) {
                    return SelectionDecision.directInvocationReference(test, source.getKey(), changedClassName);
                }
            }
        }
        for (Map.Entry<String, Set<String>> source : directInvocationOwners.entrySet()) {
            for (String intermediateClass : source.getValue()) {
                Set<String> targets = invocationGraph.getOrDefault(intermediateClass, Set.of());
                for (String changedClassName : changedClassNames) {
                    if (referencesChangedClass(targets, changedClassName)) {
                        return SelectionDecision.transitiveDirectInvocationReference(
                                test, source.getKey(), intermediateClass, changedClassName);
                    }
                }
            }
        }
        return SelectionDecision.noMatch(test);
    }

    private static boolean referencesChangedClass(Set<String> targets, String changedClassName) {
        return targets.contains(changedClassName)
                || targets.stream().anyMatch(target -> target.startsWith(changedClassName + "$"));
    }
}
