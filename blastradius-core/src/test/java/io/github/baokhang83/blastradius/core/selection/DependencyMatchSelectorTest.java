package io.github.baokhang83.blastradius.core.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DependencyMatchSelectorTest {

    private final DependencyMatchSelector selector = new DependencyMatchSelector();
    private static final TestIdentity FOO_TEST = new TestIdentity("com.example.FooTest", "checksAdd");

    @Test
    void selectsWhenATrackedDependencyChanged() {
        SelectionDecision decision = selector.select(FOO_TEST,
                Set.of("com.example.Foo", "com.example.Helper"),
                Set.of("com.example.Foo"));

        assertTrue(decision.selected());
        assertEquals(SelectionReason.DEPENDENCY_MATCH, decision.reason());
        assertEquals("com.example.Foo", decision.matchedChangedClass());
    }

    @Test
    void doesNotSelectWhenNoTrackedDependencyChanged() {
        SelectionDecision decision = selector.select(FOO_TEST,
                Set.of("com.example.Foo"),
                Set.of("com.example.Unrelated"));

        assertFalse(decision.selected());
        assertEquals(SelectionReason.NO_MATCH, decision.reason());
    }

    @Test
    void selectsWhenACompilerGeneratedNestedClassBelongsToAChangedSourceCandidate() {
        SelectionDecision decision = selector.select(FOO_TEST,
                Set.of("com.example.GreetingKt$format$1"),
                Set.of("com.example.GreetingKt"));

        assertTrue(decision.selected());
        assertEquals(SelectionReason.DEPENDENCY_MATCH, decision.reason());
        assertEquals("com.example.GreetingKt", decision.matchedChangedClass());
    }

    @Test
    void emptyDependencySetNeverMatches() {
        SelectionDecision decision = selector.select(FOO_TEST, Set.of(), Set.of("com.example.Foo"));
        assertFalse(decision.selected());
    }
}
