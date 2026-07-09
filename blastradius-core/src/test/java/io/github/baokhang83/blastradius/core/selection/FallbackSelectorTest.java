package io.github.baokhang83.blastradius.core.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.FileKind;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class FallbackSelectorTest {

    private final FallbackSelector selector = new FallbackSelector();

    @Test
    void triggersWhenAnyNonSourceFileChanged() {
        List<ChangedFile> changes = List.of(
                new ChangedFile("pom.xml", FileKind.NON_SOURCE, null),
                new ChangedFile("src/main/java/com/example/Foo.java", FileKind.JAVA_SOURCE, "com.example.Foo"));

        assertTrue(selector.shouldFallback(changes));
    }

    @Test
    void doesNotTriggerWhenOnlyJavaSourceChanged() {
        List<ChangedFile> changes = List.of(
                new ChangedFile("src/main/java/com/example/Foo.java", FileKind.JAVA_SOURCE, "com.example.Foo"));

        assertFalse(selector.shouldFallback(changes));
    }

    @Test
    void doesNotTriggerOnEmptyChanges() {
        assertFalse(selector.shouldFallback(List.of()));
    }

    @Test
    void selectProducesFallbackReason() {
        SelectionDecision decision = selector.select(new TestIdentity("com.example.FooTest", "checksAdd"));

        assertTrue(decision.selected());
        assertEquals(SelectionReason.FALLBACK_NON_SOURCE_CHANGE, decision.reason());
    }
}
