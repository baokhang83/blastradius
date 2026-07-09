package io.github.baokhang83.blastradius.validator.report;

import io.github.baokhang83.blastradius.validator.git.CommitPair;
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
        List<CommitPair> analyzedCommitPairs,
        List<CommitPair> excludedCommitPairs,
        List<WouldMissCase> wouldMissCases,
        List<FlakyFailure> flakyFailures,
        SavingsSummary savingsSummary) {}
