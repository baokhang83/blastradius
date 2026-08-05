package io.github.baokhang83.blastradius.core.selection;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.Map;
import java.util.Set;

/** Selects a test when one of its dynamically-executed classes directly references a changed class. */
final class DirectInvocationReferenceSelector {

    SelectionDecision select(TestIdentity test, Map<String, Set<String>> directInvocationOwners,
            Set<String> changedClassNames) {
        for (Map.Entry<String, Set<String>> source : directInvocationOwners.entrySet()) {
            for (String changedClassName : changedClassNames) {
                if (source.getValue().contains(changedClassName)
                        || source.getValue().stream().anyMatch(owner -> owner.startsWith(changedClassName + "$"))) {
                    return SelectionDecision.directInvocationReference(test, source.getKey(), changedClassName);
                }
            }
        }
        return SelectionDecision.noMatch(test);
    }
}
