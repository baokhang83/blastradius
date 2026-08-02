package io.github.baokhang83.blastradius.validator.report;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.validator.verdict.FlakyFailure;
import io.github.baokhang83.blastradius.validator.verdict.WouldMissCase;

/**
 * Renders an {@link AnalysisReport} as a human-readable plain-text summary. Purely a
 * rendering of the JSON report — not a second source of truth (contract note); every
 * would-miss case is rendered individually, never summarized away.
 */
public final class TextSummaryRenderer {

    public String render(AnalysisReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("Verdict: ").append(report.verdict()).append('\n');
        sb.append("History mode: ").append(report.historyMode()).append('\n');
        sb.append("Analyzed: ").append(report.analyzedCommitPairs().size()).append(" commit pair(s)");
        if (!report.excludedCommitPairs().isEmpty()) {
            sb.append(" (").append(report.excludedCommitPairs().size())
                    .append(" excluded — see report for reasons)");
        }
        sb.append('\n');
        sb.append("Skipped test classes: ").append(report.skippedTests().size()).append('\n');
        for (String skippedTest : report.skippedTests()) {
            sb.append("  - ").append(skippedTest).append('\n');
        }
        FailureCoverage coverage = report.failureCoverage();
        sb.append("Failure coverage:\n");
        sb.append("  - Pairs with newly confirmed failures: ")
                .append(coverage.pairsWithNewlyConfirmedFailures()).append('\n');
        sb.append("  - Newly confirmed failing tests: ")
                .append(coverage.newlyConfirmedFailingTests()).append('\n');
        sb.append("  - Newly confirmed failures selected: ")
                .append(coverage.selectedNewlyConfirmedFailures()).append('\n');
        sb.append("  - Newly confirmed failures skipped: ")
                .append(coverage.skippedNewlyConfirmedFailures()).append('\n');
        sb.append("Would-miss cases: ").append(report.wouldMissCases().size()).append('\n');

        for (WouldMissCase miss : report.wouldMissCases()) {
            sb.append("  - commit ").append(miss.commitPair().baseCommit())
                    .append("..").append(miss.commitPair().headCommit())
                    .append(": ").append(testLabel(miss.test())).append('\n');
            sb.append("    changed: ").append(String.join(", ", miss.changedClasses())).append('\n');
            sb.append("    not selected: ").append(miss.selectionReason()).append('\n');
        }

        SavingsSummary savings = report.savingsSummary();
        sb.append('\n');
        sb.append("Savings: ").append(savings.totalSelected()).append(" / ")
                .append(savings.totalTestExecutions()).append(" test executions selected")
                .append(String.format(" (%.1f%% skipped)%n", savings.proportionSkipped() * 100));
        sb.append("  - dependency-matched: ").append(savings.dependencyMatchedSelections()).append('\n');
        sb.append("  - fallback-driven: ").append(savings.fallbackDrivenSelections()).append('\n');
        sb.append("  - module-scoped fallback: ").append(savings.moduleScopedFallbackSelections()).append('\n');
        sb.append("  - new-or-modified: ").append(savings.newOrModifiedTestSelections()).append('\n');

        sb.append("Excluded pairs: ").append(report.excludedCommitPairs().size()).append('\n');
        sb.append("Flaky failures observed: ").append(report.flakyFailures().size())
                .append(" (excluded from verdict)\n");
        if (!report.flakyFailures().isEmpty()) {
            for (FlakyFailure flaky : report.flakyFailures()) {
                sb.append("  - commit ").append(flaky.commitPair().baseCommit())
                        .append("..").append(flaky.commitPair().headCommit())
                        .append(": ").append(testLabel(flaky.test())).append('\n');
            }
        }

        return sb.toString();
    }

    private static String testLabel(TestIdentity test) {
        return test.methodName() == null ? test.className() : test.className() + "#" + test.methodName();
    }
}
