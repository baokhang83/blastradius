package io.github.baokhang83.blastradius.validator.verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.validator.build.GroundTruthResult;
import io.github.baokhang83.blastradius.validator.build.Outcome;
import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.validator.git.CommitPair;
import io.github.baokhang83.blastradius.core.git.FileKind;
import io.github.baokhang83.blastradius.validator.report.FailureCoverage;
import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.selection.SelectionReason;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class WouldMissComparatorTest {

    private final WouldMissComparator comparator = new WouldMissComparator();
    private static final TestIdentity FOO_TEST = new TestIdentity("com.example.FooTest", "checksAdd");

    @Test
    void confirmedFailedAndNotSelectedProducesAWouldMissCase() {
        CommitPair pair = CommitPair.analyzed("base", "head",
                List.of(new ChangedFile("src/main/java/com/example/Foo.java", FileKind.JAVA_SOURCE, "com.example.Foo")));
        List<SelectionDecision> decisions = List.of(SelectionDecision.noMatch(FOO_TEST));
        List<GroundTruthResult> groundTruth = List.of(new GroundTruthResult(FOO_TEST, Outcome.CONFIRMED_FAILED));

        FailureComparison comparison = comparator.compare(pair, decisions, groundTruth, List.of());

        assertEquals(new FailureCoverage(1, 1, 0, 1), comparison.coverage());
        WouldMissCase miss = comparison.wouldMissCases().get(0);
        assertEquals(FOO_TEST, miss.test());
        assertEquals(pair, miss.commitPair());
        assertTrue(miss.changedClasses().contains("com.example.Foo"));
        assertEquals("NO_MATCH", miss.selectionReason());
    }

    @Test
    void confirmedFailedButSelectedProducesNoWouldMissCase() {
        CommitPair pair = CommitPair.analyzed("base", "head", List.of());
        List<SelectionDecision> decisions = List.of(SelectionDecision.dependencyMatch(FOO_TEST, "com.example.Foo"));
        List<GroundTruthResult> groundTruth = List.of(new GroundTruthResult(FOO_TEST, Outcome.CONFIRMED_FAILED));

        FailureComparison comparison = comparator.compare(pair, decisions, groundTruth, List.of());

        assertEquals(new FailureCoverage(1, 1, 1, 0), comparison.coverage());
        assertTrue(comparison.wouldMissCases().isEmpty());
    }

    @Test
    void passedTestsNeverProduceAWouldMissCaseEvenIfNotSelected() {
        CommitPair pair = CommitPair.analyzed("base", "head", List.of());
        List<SelectionDecision> decisions = List.of(SelectionDecision.noMatch(FOO_TEST));
        List<GroundTruthResult> groundTruth = List.of(new GroundTruthResult(FOO_TEST, Outcome.PASSED));

        FailureComparison comparison = comparator.compare(pair, decisions, groundTruth, List.of());

        assertEquals(FailureCoverage.empty(), comparison.coverage());
        assertTrue(comparison.wouldMissCases().isEmpty());
    }

    @Test
    void flakyTestsNeverProduceAWouldMissCaseEvenIfNotSelected() {
        CommitPair pair = CommitPair.analyzed("base", "head", List.of());
        List<SelectionDecision> decisions = List.of(SelectionDecision.noMatch(FOO_TEST));
        List<GroundTruthResult> groundTruth = List.of(new GroundTruthResult(FOO_TEST, Outcome.FLAKY));

        FailureComparison comparison = comparator.compare(pair, decisions, groundTruth, List.of());

        assertEquals(FailureCoverage.empty(), comparison.coverage());
        assertTrue(comparison.wouldMissCases().isEmpty());
    }

    @Test
    void missingSelectionDecisionForAFailedTestIsTreatedAsNotSelected() {
        CommitPair pair = CommitPair.analyzed("base", "head", List.of());
        List<GroundTruthResult> groundTruth = List.of(new GroundTruthResult(FOO_TEST, Outcome.CONFIRMED_FAILED));

        FailureComparison comparison = comparator.compare(pair, List.of(), groundTruth, List.of());

        assertEquals(new FailureCoverage(1, 1, 0, 1), comparison.coverage());
        assertEquals(1, comparison.wouldMissCases().size());
    }

    @Test
    void confirmedFailedOnBaseAlreadySuppressesTheWouldMissCase() {
        // e.g. shenyu-admin's RoleMapperTest#testSelectAll: broken by schema.sql's seed
        // data on every commit, entirely unrelated to this pair's diff. Selection had
        // nothing to catch — the failure predates the change.
        CommitPair pair = CommitPair.analyzed("base", "head",
                List.of(new ChangedFile("src/main/java/com/example/Foo.java", FileKind.JAVA_SOURCE, "com.example.Foo")));
        List<SelectionDecision> decisions = List.of(SelectionDecision.noMatch(FOO_TEST));
        List<GroundTruthResult> groundTruth = List.of(new GroundTruthResult(FOO_TEST, Outcome.CONFIRMED_FAILED));
        List<GroundTruthResult> baseGroundTruth = List.of(new GroundTruthResult(FOO_TEST, Outcome.CONFIRMED_FAILED));

        FailureComparison comparison = comparator.compare(pair, decisions, groundTruth, baseGroundTruth);

        assertEquals(FailureCoverage.empty(), comparison.coverage());
        assertTrue(comparison.wouldMissCases().isEmpty());
    }

    @Test
    void baseFlakinessDoesNotSuppressAGenuineWouldMissCase() {
        // Only a CONFIRMED failure at base counts as pre-existing; a base-flaky result
        // means the test genuinely passed there once confirmed, so a real regression at
        // head must still be reported.
        CommitPair pair = CommitPair.analyzed("base", "head", List.of());
        List<SelectionDecision> decisions = List.of(SelectionDecision.noMatch(FOO_TEST));
        List<GroundTruthResult> groundTruth = List.of(new GroundTruthResult(FOO_TEST, Outcome.CONFIRMED_FAILED));
        List<GroundTruthResult> baseGroundTruth = List.of(new GroundTruthResult(FOO_TEST, Outcome.FLAKY));

        FailureComparison comparison = comparator.compare(pair, decisions, groundTruth, baseGroundTruth);

        assertEquals(new FailureCoverage(1, 1, 0, 1), comparison.coverage());
        assertEquals(1, comparison.wouldMissCases().size());
    }

    @Test
    void coverageCountsSelectedAndSkippedNewFailuresWithinOnePair() {
        TestIdentity selected = new TestIdentity("com.example.SelectedTest", "checksSelected");
        CommitPair pair = CommitPair.analyzed("base", "head", List.of());
        List<SelectionDecision> decisions = List.of(
                SelectionDecision.dependencyMatch(selected, "com.example.Shared"),
                SelectionDecision.noMatch(FOO_TEST));
        List<GroundTruthResult> groundTruth = List.of(
                new GroundTruthResult(selected, Outcome.CONFIRMED_FAILED),
                new GroundTruthResult(FOO_TEST, Outcome.CONFIRMED_FAILED));

        FailureComparison comparison = comparator.compare(pair, decisions, groundTruth, List.of());

        assertEquals(new FailureCoverage(1, 2, 1, 1), comparison.coverage());
        assertEquals(1, comparison.wouldMissCases().size());
    }
}
