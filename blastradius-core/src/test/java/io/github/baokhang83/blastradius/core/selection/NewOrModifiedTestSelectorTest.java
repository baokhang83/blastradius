package io.github.baokhang83.blastradius.core.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NewOrModifiedTestSelectorTest {

    private final NewOrModifiedTestSelector selector = new NewOrModifiedTestSelector();
    private static final TestIdentity FOO_TEST = new TestIdentity("com.example.FooTest", "checksAdd");

    @Test
    void appliesWhenTestHasNoPriorDependencyBaseline() {
        assertTrue(selector.appliesTo(FOO_TEST, true, Set.of()));
    }

    @Test
    void appliesWhenTestsOwnClassWasChanged() {
        assertTrue(selector.appliesTo(FOO_TEST, false, Set.of("com.example.FooTest")));
    }

    @Test
    void doesNotApplyWhenNeitherConditionHolds() {
        assertFalse(selector.appliesTo(FOO_TEST, false, Set.of("com.example.SomeOtherClass")));
    }

    @Test
    void selectProducesNewOrModifiedReason() {
        SelectionDecision decision = selector.select(FOO_TEST);

        assertTrue(decision.selected());
        assertEquals(SelectionReason.NEW_OR_MODIFIED_TEST, decision.reason());
    }
}
