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
                changed,
                Set.of());

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
                changed,
                Set.of());

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
                changed,
                Set.of());

        SelectionDecision matched = decisions.stream().filter(d -> d.test().equals(MATCHED_TEST)).findFirst().orElseThrow();
        SelectionDecision unrelated = decisions.stream().filter(d -> d.test().equals(UNRELATED_TEST)).findFirst().orElseThrow();

        assertTrue(matched.selected());
        assertEquals(SelectionReason.DEPENDENCY_MATCH, matched.reason());
        assertEquals(SelectionReason.NO_MATCH, unrelated.reason());
    }

    @Test
    void directInvocationReferenceSelectsOnlyTheTestThatExecutedItsSourceClass() {
        List<ChangedFile> changed = List.of(new ChangedFile(
                "src/main/java/com/example/QueryParser.java", FileKind.JAVA_SOURCE, "com.example.QueryParser"));

        List<SelectionDecision> decisions = engine.selectAll(
                Set.of(MATCHED_TEST, UNRELATED_TEST),
                Map.of(MATCHED_TEST, Set.of("com.example.Selector"), UNRELATED_TEST, Set.of("com.example.Other")),
                Map.of(MATCHED_TEST, Map.of("com.example.Selector", Set.of("com.example.QueryParser"))),
                Set.of(),
                changed,
                Set.of());

        SelectionDecision matched = decisions.stream().filter(d -> d.test().equals(MATCHED_TEST)).findFirst().orElseThrow();
        SelectionDecision unrelated = decisions.stream().filter(d -> d.test().equals(UNRELATED_TEST)).findFirst().orElseThrow();

        assertTrue(matched.selected());
        assertEquals(SelectionReason.DIRECT_INVOCATION_REFERENCE, matched.reason());
        assertEquals("com.example.QueryParser", matched.matchedChangedClass());
        assertEquals("com.example.Selector", matched.directInvocationSourceClass());
        assertEquals(SelectionReason.NO_MATCH, unrelated.reason());
    }

    @Test
    void transitiveDirectInvocationReferenceSelectsARecordedTwoHopPath() {
        TestIdentity graphProvider = new TestIdentity("com.example.GraphProviderTest", "loadsSelector");
        List<ChangedFile> changed = List.of(new ChangedFile(
                "src/main/java/com/example/QueryParser.java", FileKind.JAVA_SOURCE, "com.example.QueryParser"));

        List<SelectionDecision> decisions = engine.selectAll(
                Set.of(MATCHED_TEST, graphProvider),
                Map.of(MATCHED_TEST, Set.of("com.example.DataUtil"), graphProvider, Set.of("com.example.Selector")),
                Map.of(
                        MATCHED_TEST, Map.of("com.example.DataUtil", Set.of("com.example.Selector")),
                        graphProvider, Map.of("com.example.Selector", Set.of("com.example.QueryParser"))),
                Set.of(),
                changed,
                Set.of());

        SelectionDecision matched = decisions.stream().filter(d -> d.test().equals(MATCHED_TEST)).findFirst().orElseThrow();

        assertTrue(matched.selected());
        assertEquals(SelectionReason.TRANSITIVE_DIRECT_INVOCATION_REFERENCE, matched.reason());
        assertEquals("com.example.QueryParser", matched.matchedChangedClass());
        assertEquals("com.example.DataUtil", matched.directInvocationSourceClass());
        assertEquals("com.example.Selector", matched.directInvocationIntermediateClass());
    }

    @Test
    void transitiveDirectInvocationReferenceDoesNotTraverseBeyondTwoHops() {
        TestIdentity firstProvider = new TestIdentity("com.example.FirstProviderTest", "loadsHelper");
        TestIdentity secondProvider = new TestIdentity("com.example.SecondProviderTest", "loadsQueryParser");
        List<ChangedFile> changed = List.of(new ChangedFile(
                "src/main/java/com/example/QueryParser.java", FileKind.JAVA_SOURCE, "com.example.QueryParser"));

        List<SelectionDecision> decisions = engine.selectAll(
                Set.of(MATCHED_TEST, firstProvider, secondProvider),
                Map.of(),
                Map.of(
                        MATCHED_TEST, Map.of("com.example.DataUtil", Set.of("com.example.Selector")),
                        firstProvider, Map.of("com.example.Selector", Set.of("com.example.Helper")),
                        secondProvider, Map.of("com.example.Helper", Set.of("com.example.QueryParser"))),
                Set.of(),
                changed,
                Set.of());

        SelectionDecision matched = decisions.stream().filter(d -> d.test().equals(MATCHED_TEST)).findFirst().orElseThrow();

        assertEquals(SelectionReason.NO_MATCH, matched.reason());
    }

    @Test
    void directInvocationReferenceTakesPrecedenceOverATwoHopReference() {
        TestIdentity graphProvider = new TestIdentity("com.example.GraphProviderTest", "loadsSelector");
        List<ChangedFile> changed = List.of(new ChangedFile(
                "src/main/java/com/example/QueryParser.java", FileKind.JAVA_SOURCE, "com.example.QueryParser"));

        SelectionDecision decision = engine.selectAll(
                        Set.of(MATCHED_TEST, graphProvider),
                        Map.of(),
                        Map.of(
                                MATCHED_TEST, Map.of("com.example.DataUtil", Set.of(
                                        "com.example.Selector", "com.example.QueryParser")),
                                graphProvider, Map.of("com.example.Selector", Set.of("com.example.QueryParser"))),
                        Set.of(),
                        changed,
                        Set.of())
                .stream()
                .filter(candidate -> candidate.test().equals(MATCHED_TEST))
                .findFirst()
                .orElseThrow();

        assertEquals(SelectionReason.DIRECT_INVOCATION_REFERENCE, decision.reason());
        assertEquals(null, decision.directInvocationIntermediateClass());
    }

    @Test
    void ordinaryDependencyMatchTakesPrecedenceOverDirectInvocationReference() {
        List<ChangedFile> changed = List.of(new ChangedFile(
                "src/main/java/com/example/QueryParser.java", FileKind.JAVA_SOURCE, "com.example.QueryParser"));

        SelectionDecision decision = engine.selectAll(
                        Set.of(MATCHED_TEST),
                        Map.of(MATCHED_TEST, Set.of("com.example.QueryParser")),
                        Map.of(MATCHED_TEST, Map.of("com.example.Selector", Set.of("com.example.QueryParser"))),
                        Set.of(),
                        changed,
                        Set.of())
                .getFirst();

        assertEquals(SelectionReason.DEPENDENCY_MATCH, decision.reason());
        assertEquals(null, decision.directInvocationSourceClass());
        assertEquals(null, decision.directInvocationIntermediateClass());
    }

    @Test
    void kotlinFileFacadeCandidateContributesToDependencyMatching() {
        List<ChangedFile> changed = List.of(new ChangedFile(
                "src/main/kotlin/com/example/Greeting.kt", FileKind.JAVA_SOURCE, "com.example.Greeting"));

        List<SelectionDecision> decisions = engine.selectAll(
                Set.of(MATCHED_TEST), Map.of(MATCHED_TEST, Set.of("com.example.GreetingKt")), Set.of(), changed,
                Set.of());

        assertTrue(decisions.getFirst().selected());
        assertEquals("com.example.GreetingKt", decisions.getFirst().matchedChangedClass());
    }

    @Test
    void producesExactlyOneDecisionPerTest() {
        List<SelectionDecision> decisions = engine.selectAll(
                Set.of(MATCHED_TEST, UNRELATED_TEST, NEW_TEST), Map.of(), Set.of(), List.of(), Set.of());

        assertEquals(3, decisions.size());
    }

    @Test
    void ambientFallbackTriggersWhenAChangedClassIsAmbient() {
        List<ChangedFile> changed = List.of(
                new ChangedFile("src/main/java/com/example/Foo.java", FileKind.JAVA_SOURCE, "com.example.Foo"));

        List<SelectionDecision> decisions = engine.selectAll(
                Set.of(MATCHED_TEST, UNRELATED_TEST),
                Map.of(MATCHED_TEST, Set.of("com.example.Foo")),
                Set.of(),
                changed,
                Set.of("com.example.Foo"));

        assertTrue(decisions.stream().allMatch(SelectionDecision::selected));
        assertTrue(decisions.stream()
                .allMatch(d -> d.reason() == SelectionReason.FALLBACK_AMBIENT_DEPENDENCY));
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
                changed,
                Set.of());

        assertTrue(decisions.stream().noneMatch(SelectionDecision::selected));
        assertTrue(decisions.stream()
                .noneMatch(d -> d.reason() == SelectionReason.FALLBACK_NON_SOURCE_CHANGE));
    }
}
