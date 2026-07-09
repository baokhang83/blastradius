package io.github.baokhang83.blastradius.plugin.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.IndexApplicability;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests (tasks.md T034) for populating a {@link BuildReport} from a {@code SELECT} branch's
 * computed {@link SelectionDecision} list (FR-008, FR-009) — the counts and invariants a
 * {@code SelectMojo} SELECT-branch report must satisfy per contracts/mojo-and-index-contract.md.
 */
class BuildReportPopulationTest {

    @Test
    void derivesCountsAndModeFromTheDecisionList() {
        TestIdentity matched = new TestIdentity("com.example.FooTest", "checksFoo");
        TestIdentity unrelated = new TestIdentity("com.example.BarTest", "checksBar");
        TestIdentity added = new TestIdentity("com.example.BazTest", "checksBaz");
        List<SelectionDecision> decisions = List.of(
                SelectionDecision.dependencyMatch(matched, "com.example.Foo"),
                SelectionDecision.noMatch(unrelated),
                SelectionDecision.newOrModifiedTest(added));

        IndexApplicability applicability = IndexApplicability.applicable(
                new DependencyIndex("abc123", "2026-07-09T10:00:00Z", List.of()));

        BuildReport report = BuildReport.forSelect(applicability, decisions);

        assertEquals(BuildReport.Mode.SELECT, report.mode());
        assertEquals(IndexApplicability.Status.APPLICABLE, report.indexApplicability());
        assertEquals(3, report.totalCount());
        assertEquals(2, report.selectedCount());
        assertEquals(decisions, report.decisions());
        assertNull(report.updatedIndex(), "SELECT mode never produces/refreshes an index");
    }

    @Test
    void anEmptySuiteHasZeroCounts() {
        IndexApplicability applicability = IndexApplicability.applicable(
                new DependencyIndex("abc123", "2026-07-09T10:00:00Z", List.of()));

        BuildReport report = BuildReport.forSelect(applicability, List.of());

        assertEquals(0, report.totalCount());
        assertEquals(0, report.selectedCount());
    }
}
