package io.github.baokhang83.blastradius.core.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.FileKind;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SelectionEngineTest {

    private final SelectionEngine engine = new SelectionEngine();

    private static final TestIdentity MATCHED_TEST = new TestIdentity("com.example.MatchedTest", "checksFoo");
    private static final TestIdentity UNRELATED_TEST = new TestIdentity("com.example.UnrelatedTest", "checksBar");
    private static final TestIdentity NEW_TEST = new TestIdentity("com.example.NewTest", "checksBaz");

    @Test
    void fallbackShortCircuitsAndSelectsEveryTestWhenNonSourceFileChanged() {
        List<ChangedFile> changed = List.of(new ChangedFile("pom.xml", FileKind.NON_SOURCE, null));

        List<SelectionDecision> decisions = engine.selectAll(
                Set.of(MATCHED_TEST, UNRELATED_TEST),
                Map.of(MATCHED_TEST, Set.of("com.example.Foo")),
                Set.of(),
                changed);

        assertTrue(decisions.stream().allMatch(SelectionDecision::selected));
        assertTrue(decisions.stream()
                .allMatch(d -> d.reason() == SelectionReason.FALLBACK_NON_SOURCE_CHANGE));
    }

    @Test
    void newOrModifiedTestTakesPrecedenceOverDependencyMatching() {
        List<ChangedFile> changed = List.of(
                new ChangedFile("src/main/java/com/example/Foo.java", FileKind.JAVA_SOURCE, "com.example.Foo"));

        List<SelectionDecision> decisions = engine.selectAll(
                Set.of(NEW_TEST),
                Map.of(),
                Set.of(NEW_TEST),
                changed);

        assertEquals(1, decisions.size());
        assertEquals(SelectionReason.NEW_OR_MODIFIED_TEST, decisions.get(0).reason());
    }

    @Test
    void ordinaryDependencyMatchingAppliesWhenNoFallbackOrNewModifiedTest() {
        List<ChangedFile> changed = List.of(
                new ChangedFile("src/main/java/com/example/Foo.java", FileKind.JAVA_SOURCE, "com.example.Foo"));

        List<SelectionDecision> decisions = engine.selectAll(
                Set.of(MATCHED_TEST, UNRELATED_TEST),
                Map.of(MATCHED_TEST, Set.of("com.example.Foo"), UNRELATED_TEST, Set.of("com.example.Other")),
                Set.of(),
                changed);

        SelectionDecision matched = decisions.stream().filter(d -> d.test().equals(MATCHED_TEST)).findFirst().orElseThrow();
        SelectionDecision unrelated = decisions.stream().filter(d -> d.test().equals(UNRELATED_TEST)).findFirst().orElseThrow();

        assertTrue(matched.selected());
        assertEquals(SelectionReason.DEPENDENCY_MATCH, matched.reason());
        assertEquals(SelectionReason.NO_MATCH, unrelated.reason());
    }

    @Test
    void producesExactlyOneDecisionPerTest() {
        List<SelectionDecision> decisions = engine.selectAll(
                Set.of(MATCHED_TEST, UNRELATED_TEST, NEW_TEST), Map.of(), Set.of(), List.of());

        assertEquals(3, decisions.size());
    }

    @Test
    void inertOnlyChangeSelectsNoTestsAndDoesNotFallback() {
        // A README-only change: INERT is neither fallback-triggering (NON_SOURCE) nor
        // match-contributing (JAVA_SOURCE), so every test is unselected — zero tests run.
        List<ChangedFile> changed = List.of(new ChangedFile("README.md", FileKind.INERT, null));

        List<SelectionDecision> decisions = engine.selectAll(
                Set.of(MATCHED_TEST, UNRELATED_TEST),
                Map.of(MATCHED_TEST, Set.of("com.example.Foo")),
                Set.of(),
                changed);

        assertTrue(decisions.stream().noneMatch(SelectionDecision::selected));
        assertTrue(decisions.stream()
                .noneMatch(d -> d.reason() == SelectionReason.FALLBACK_NON_SOURCE_CHANGE));
    }
}
