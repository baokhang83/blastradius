package io.github.baokhang83.blastradius.validator.report;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.validator.git.CommitPair;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.validator.verdict.Verdict;
import io.github.baokhang83.blastradius.validator.verdict.WouldMissCase;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextSummaryRendererTest {

    private final TextSummaryRenderer renderer = new TextSummaryRenderer();

    @Test
    void rendersAPassVerdictWithSavingsFigures() {
        SavingsSummary savings = new SavingsSummary(10, 4, 0.6, 1, 2, 1);
        AnalysisReport report = new AnalysisReport(Verdict.PASS, List.of(), List.of(), List.of(), List.of(), savings);

        String text = renderer.render(report);

        assertTrue(text.contains("Verdict: PASS"));
        assertTrue(text.contains("Would-miss cases: 0"));
        assertTrue(text.contains("4"));
        assertTrue(text.contains("10"));
        assertTrue(text.contains("dependency-matched: 2"));
        assertTrue(text.contains("fallback-driven: 1"));
    }

    @Test
    void rendersAFailVerdictWithWouldMissCaseDetail() {
        CommitPair pair = CommitPair.analyzed("base123", "head456", List.of());
        WouldMissCase miss = new WouldMissCase(pair, new TestIdentity("com.example.FooTest", "checksAdd"),
                List.of("com.example.Foo"), "NO_MATCH");
        SavingsSummary savings = new SavingsSummary(1, 0, 1.0, 0, 0, 0);
        AnalysisReport report = new AnalysisReport(Verdict.FAIL, List.of(pair), List.of(), List.of(miss), List.of(), savings);

        String text = renderer.render(report);

        assertTrue(text.contains("Verdict: FAIL"));
        assertTrue(text.contains("Would-miss cases: 1"));
        assertTrue(text.contains("com.example.FooTest#checksAdd"));
        assertTrue(text.contains("com.example.Foo"));
        assertTrue(text.contains("NO_MATCH"));
        assertTrue(text.contains("base123"));
        assertTrue(text.contains("head456"));
    }

    @Test
    void everyWouldMissCaseAppearsIndividuallyNoneSummarizedAway() {
        CommitPair pair = CommitPair.analyzed("b", "h", List.of());
        List<WouldMissCase> misses = List.of(
                new WouldMissCase(pair, new TestIdentity("com.example.ATest", "a"), List.of("com.example.A"), "NO_MATCH"),
                new WouldMissCase(pair, new TestIdentity("com.example.BTest", "b"), List.of("com.example.B"), "NO_MATCH"),
                new WouldMissCase(pair, new TestIdentity("com.example.CTest", "c"), List.of("com.example.C"), "NO_MATCH"));
        AnalysisReport report = new AnalysisReport(Verdict.FAIL, List.of(pair), List.of(), misses, List.of(),
                new SavingsSummary(3, 0, 1.0, 0, 0, 0));

        String text = renderer.render(report);

        assertTrue(text.contains("com.example.ATest#a"));
        assertTrue(text.contains("com.example.BTest#b"));
        assertTrue(text.contains("com.example.CTest#c"));
        assertTrue(text.contains("Would-miss cases: 3"));
    }
}
