package io.github.baokhang83.blastradius.core.selection;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.Set;

/** Always selects a test that is new (no prior baseline) or whose own file changed (FR-007). */
public final class NewOrModifiedTestSelector {

    public boolean appliesTo(TestIdentity test, boolean hasNoPriorBaseline, Set<String> changedClassNames) {
        return hasNoPriorBaseline || changedClassNames.contains(test.className());
    }

    public SelectionDecision select(TestIdentity test) {
        return SelectionDecision.newOrModifiedTest(test);
    }
}
