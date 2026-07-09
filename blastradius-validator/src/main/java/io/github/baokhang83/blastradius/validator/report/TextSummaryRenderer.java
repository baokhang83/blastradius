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
        sb.append("Analyzed: ").append(report.analyzedCommitPairs().size()).append(" commit pair(s)");
        if (!report.excludedCommitPairs().isEmpty()) {
            sb.append(" (").append(report.excludedCommitPairs().size())
                    .append(" excluded — see report for reasons)");
        }
        sb.append('\n');
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
        sb.append("  - new-or-modified: ").append(savings.newOrModifiedTestSelections()).append('\n');

        if (!report.flakyFailures().isEmpty()) {
            sb.append("Flaky failures observed: ").append(report.flakyFailures().size())
                    .append(" (excluded from verdict)\n");
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
