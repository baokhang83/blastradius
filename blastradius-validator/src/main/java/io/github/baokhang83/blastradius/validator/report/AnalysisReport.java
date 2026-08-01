package io.github.baokhang83.blastradius.validator.report;

import io.github.baokhang83.blastradius.validator.git.CommitPair;
import io.github.baokhang83.blastradius.validator.git.HistoryMode;
import io.github.baokhang83.blastradius.validator.verdict.FlakyFailure;
import io.github.baokhang83.blastradius.validator.verdict.Verdict;
import io.github.baokhang83.blastradius.validator.verdict.WouldMissCase;
import java.util.List;

/**
 * The run's complete output — the single JSON source of truth (FR-010, SC-005).
 *
 * @param excludedCommitPairs pairs that could not be built/tested (FR-009); excluded
 *                            from {@code wouldMissCases} and {@code savingsSummary}
 * @param flakyFailures       tests that failed once but passed on confirmation (FR-014);
 *                            never affect the verdict, reported for transparency only
 */
public record AnalysisReport(
        Verdict verdict,
        HistoryMode historyMode,
        List<CommitPair> analyzedCommitPairs,
        List<CommitPair> excludedCommitPairs,
        FailureCoverage failureCoverage,
        List<WouldMissCase> wouldMissCases,
        List<FlakyFailure> flakyFailures,
        SavingsSummary savingsSummary) {

    /** Compatibility constructor for callers that do not yet supply the newly-visible fields. */
    public AnalysisReport(
            Verdict verdict,
            List<CommitPair> analyzedCommitPairs,
            List<CommitPair> excludedCommitPairs,
            List<WouldMissCase> wouldMissCases,
            List<FlakyFailure> flakyFailures,
            SavingsSummary savingsSummary) {
        this(verdict, HistoryMode.ALL_PARENTS, analyzedCommitPairs, excludedCommitPairs,
                legacyFailureCoverage(wouldMissCases), wouldMissCases, flakyFailures, savingsSummary);
    }

    private static FailureCoverage legacyFailureCoverage(List<WouldMissCase> wouldMissCases) {
        int misses = wouldMissCases.size();
        int pairs = (int) wouldMissCases.stream().map(WouldMissCase::commitPair).distinct().count();
        return new FailureCoverage(pairs, misses, 0, misses);
    }
}
