package io.github.baokhang83.blastradius.validator.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.baokhang83.blastradius.validator.git.CommitPair;
import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.validator.verdict.Verdict;
import io.github.baokhang83.blastradius.validator.verdict.WouldMissCase;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Validates the invariants in contracts/cli-and-report-contract.md — not just documents
 * them — via a real JSON round-trip through Jackson (the same mechanism {@link ReportWriter}
 * uses).
 */
class AnalysisReportContractTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SavingsSummaryAggregator aggregator = new SavingsSummaryAggregator();

    @Test
    void passVerdictIffWouldMissCasesEmpty() throws Exception {
        AnalysisReport passReport = new AnalysisReport(Verdict.PASS, List.of(), List.of(), List.of(), List.of(),
                aggregator.aggregate(List.of()));
        assertTrue(roundTrip(passReport).wouldMissCases().isEmpty());
        assertEquals(Verdict.PASS, roundTrip(passReport).verdict());

        CommitPair pair = CommitPair.analyzed("b", "h", List.of());
        WouldMissCase miss = new WouldMissCase(pair, new TestIdentity("com.example.FooTest", "a"),
                List.of(), "NO_MATCH");
        AnalysisReport failReport = new AnalysisReport(Verdict.FAIL, List.of(pair), List.of(), List.of(miss), List.of(),
                aggregator.aggregate(List.of()));
        assertFalseEmpty(roundTrip(failReport).wouldMissCases());
        assertEquals(Verdict.FAIL, roundTrip(failReport).verdict());
    }

    @Test
    void savingsSummaryBucketsSumToTotalSelectedAcrossTheThreeSelectionReasons() throws Exception {
        List<SelectionDecision> decisions = Stream.of(
                SelectionDecision.dependencyMatch(new TestIdentity("com.example.ATest", "a"), "com.example.X"),
                SelectionDecision.fallback(new TestIdentity("com.example.BTest", "b")),
                SelectionDecision.newOrModifiedTest(new TestIdentity("com.example.CTest", "c")),
                SelectionDecision.noMatch(new TestIdentity("com.example.DTest", "d"))
        ).toList();

        AnalysisReport report = new AnalysisReport(Verdict.PASS, List.of(), List.of(), List.of(), List.of(),
                aggregator.aggregate(decisions));
        AnalysisReport parsed = roundTrip(report);

        SavingsSummary savings = parsed.savingsSummary();
        assertEquals(savings.totalSelected(),
                savings.dependencyMatchedSelections() + savings.fallbackDrivenSelections()
                        + savings.newOrModifiedTestSelections());
    }

    @Test
    void everyWouldMissCaseCommitPairReferencesAnAnalyzedPair() throws Exception {
        CommitPair pairA = CommitPair.analyzed("a1", "a2", List.of());
        CommitPair pairB = CommitPair.analyzed("b1", "b2", List.of());
        WouldMissCase missInA = new WouldMissCase(pairA, new TestIdentity("com.example.FooTest", "a"),
                List.of(), "NO_MATCH");

        AnalysisReport report = new AnalysisReport(Verdict.FAIL, List.of(pairA, pairB), List.of(), List.of(missInA),
                List.of(), aggregator.aggregate(List.of()));
        AnalysisReport parsed = roundTrip(report);

        assertTrue(parsed.analyzedCommitPairs().contains(parsed.wouldMissCases().get(0).commitPair()));
    }

    @Test
    void reportIsFullyReconstructableFromJsonAlone() throws Exception {
        CommitPair pair = CommitPair.analyzed("base", "head", List.of());
        WouldMissCase miss = new WouldMissCase(pair, new TestIdentity("com.example.FooTest", "checksAdd"),
                List.of("com.example.Foo"), "NO_MATCH");
        AnalysisReport original = new AnalysisReport(Verdict.FAIL, List.of(pair), List.of(), List.of(miss), List.of(),
                aggregator.aggregate(List.of(SelectionDecision.noMatch(new TestIdentity("com.example.FooTest", "checksAdd")))));

        AnalysisReport parsed = roundTrip(original);

        assertEquals(original, parsed);
    }

    private AnalysisReport roundTrip(AnalysisReport report) throws Exception {
        String json = mapper.writeValueAsString(report);
        return mapper.readValue(json, AnalysisReport.class);
    }

    private static void assertFalseEmpty(List<WouldMissCase> misses) {
        assertTrue(!misses.isEmpty());
    }
}
