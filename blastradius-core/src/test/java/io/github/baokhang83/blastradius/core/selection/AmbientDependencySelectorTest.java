package io.github.baokhang83.blastradius.core.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AmbientDependencySelectorTest {

    private final AmbientDependencySelector selector = new AmbientDependencySelector();

    @Test
    void triggersWhenAChangedClassIsAmbient() {
        assertTrue(selector.shouldFallback(Set.of("com.example.Foo"), Set.of("com.example.Foo", "java.lang.String")));
    }

    @Test
    void doesNotTriggerWhenNoChangedClassIsAmbient() {
        assertFalse(selector.shouldFallback(Set.of("com.example.Foo"), Set.of("java.lang.String")));
    }

    @Test
    void doesNotTriggerWhenAmbientSetIsEmpty() {
        assertFalse(selector.shouldFallback(Set.of("com.example.Foo"), Set.of()));
    }

    @Test
    void doesNotTriggerWhenNoClassesChanged() {
        assertFalse(selector.shouldFallback(Set.of(), Set.of("com.example.Foo")));
    }

    @Test
    void selectProducesFallbackAmbientDependencyReason() {
        SelectionDecision decision = selector.select(new TestIdentity("com.example.FooTest", "checksAdd"));

        assertTrue(decision.selected());
        assertEquals(SelectionReason.FALLBACK_AMBIENT_DEPENDENCY, decision.reason());
    }
}
