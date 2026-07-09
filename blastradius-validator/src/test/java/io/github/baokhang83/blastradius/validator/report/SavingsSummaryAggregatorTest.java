package io.github.baokhang83.blastradius.validator.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class SavingsSummaryAggregatorTest {

    private final SavingsSummaryAggregator aggregator = new SavingsSummaryAggregator();

    private static TestIdentity test(String name) {
        return new TestIdentity("com.example." + name, "run");
    }

    @Test
    void aggregatesCountsAndProportionAcrossAllReasons() {
        List<SelectionDecision> decisions = List.of(
                SelectionDecision.dependencyMatch(test("A"), "com.example.Dep"),
                SelectionDecision.dependencyMatch(test("B"), "com.example.Dep"),
                SelectionDecision.fallback(test("C")),
                SelectionDecision.newOrModifiedTest(test("D")),
                SelectionDecision.noMatch(test("E")),
                SelectionDecision.noMatch(test("F")));

        SavingsSummary summary = aggregator.aggregate(decisions);

        assertEquals(6, summary.totalTestExecutions());
        assertEquals(4, summary.totalSelected());
        assertEquals(2, summary.dependencyMatchedSelections());
        assertEquals(1, summary.fallbackDrivenSelections());
        assertEquals(1, summary.newOrModifiedTestSelections());
        assertEquals(1.0 / 3.0, summary.proportionSkipped(), 1e-9);
    }

    @Test
    void bucketsSumToTotalSelected() {
        List<SelectionDecision> decisions = List.of(
                SelectionDecision.dependencyMatch(test("A"), "com.example.Dep"),
                SelectionDecision.fallback(test("B")),
                SelectionDecision.newOrModifiedTest(test("C")),
                SelectionDecision.noMatch(test("D")));

        SavingsSummary summary = aggregator.aggregate(decisions);

        assertEquals(summary.totalSelected(),
                summary.dependencyMatchedSelections() + summary.fallbackDrivenSelections()
                        + summary.newOrModifiedTestSelections());
    }

    @Test
    void emptyDecisionsYieldsZeroedSummaryWithoutDivideByZero() {
        SavingsSummary summary = aggregator.aggregate(List.of());

        assertEquals(0, summary.totalTestExecutions());
        assertEquals(0, summary.totalSelected());
        assertEquals(0.0, summary.proportionSkipped());
    }

    @Test
    void allSelectedYieldsZeroProportionSkipped() {
        List<SelectionDecision> decisions = List.of(SelectionDecision.fallback(test("A")));
        assertEquals(0.0, aggregator.aggregate(decisions).proportionSkipped());
    }
}
