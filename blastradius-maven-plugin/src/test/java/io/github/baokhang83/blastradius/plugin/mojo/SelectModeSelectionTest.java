package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.FileKind;
import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.selection.SelectionReason;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex.TestDependencyEntry;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SelectModeSelectionTest {

    @Test
    void computesOneDecisionPerTestUsingTheCoreSelectionEngine() {
        TestIdentity matchedTest = new TestIdentity("com.example.MatchedTest", "checksFoo");
        TestIdentity unrelatedTest = new TestIdentity("com.example.UnrelatedTest", "checksBar");
        Set<TestIdentity> allTests = Set.of(matchedTest, unrelatedTest);

        DependencyIndex index = new DependencyIndex("abc123", "2026-07-09T10:00:00Z", List.of(
                new TestDependencyEntry(matchedTest, Set.of("com.example.Foo")),
                new TestDependencyEntry(unrelatedTest, Set.of("com.example.Other"))));

        List<ChangedFile> changedFiles = List.of(
                new ChangedFile("src/main/java/com/example/Foo.java", FileKind.JAVA_SOURCE, "com.example.Foo"));

        List<SelectionDecision> decisions = SelectMojo.computeDecisions(allTests, index, changedFiles);

        assertEquals(2, decisions.size());
        SelectionDecision matched = decisions.stream().filter(d -> d.test().equals(matchedTest)).findFirst().orElseThrow();
        SelectionDecision unrelated = decisions.stream().filter(d -> d.test().equals(unrelatedTest)).findFirst().orElseThrow();

        assertTrue(matched.selected());
        assertEquals(SelectionReason.DEPENDENCY_MATCH, matched.reason());
        assertEquals(SelectionReason.NO_MATCH, unrelated.reason());
    }

    @Test
    void aTestWithNoTrackedBaselineIsAlwaysSelected() {
        TestIdentity newTest = new TestIdentity("com.example.NewTest", "checksBaz");
        Set<TestIdentity> allTests = Set.of(newTest);
        DependencyIndex index = new DependencyIndex("abc123", "2026-07-09T10:00:00Z", List.of());
        List<ChangedFile> changedFiles = List.of();

        List<SelectionDecision> decisions = SelectMojo.computeDecisions(allTests, index, changedFiles);

        assertEquals(1, decisions.size());
        assertTrue(decisions.get(0).selected());
        assertEquals(SelectionReason.NEW_OR_MODIFIED_TEST, decisions.get(0).reason());
    }
}
