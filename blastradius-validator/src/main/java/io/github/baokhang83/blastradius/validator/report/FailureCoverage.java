package io.github.baokhang83.blastradius.validator.report;

/** Aggregate coverage of newly confirmed test failures observed during historical replay. */
public record FailureCoverage(
        int pairsWithNewlyConfirmedFailures,
        int newlyConfirmedFailingTests,
        int selectedNewlyConfirmedFailures,
        int skippedNewlyConfirmedFailures) {

    public FailureCoverage {
        if (pairsWithNewlyConfirmedFailures < 0 || newlyConfirmedFailingTests < 0
                || selectedNewlyConfirmedFailures < 0 || skippedNewlyConfirmedFailures < 0) {
            throw new IllegalArgumentException("failure coverage counts must not be negative");
        }
        if (selectedNewlyConfirmedFailures + skippedNewlyConfirmedFailures != newlyConfirmedFailingTests) {
            throw new IllegalArgumentException("selected and skipped failures must sum to newly confirmed failures");
        }
        if (pairsWithNewlyConfirmedFailures > newlyConfirmedFailingTests) {
            throw new IllegalArgumentException("failure-bearing pairs cannot exceed newly confirmed failures");
        }
    }

    /** No newly confirmed failures were observed. */
    public static FailureCoverage empty() {
        return new FailureCoverage(0, 0, 0, 0);
    }

    /** Combines disjoint per-pair coverage results into a window-wide total. */
    public FailureCoverage plus(FailureCoverage other) {
        return new FailureCoverage(
                pairsWithNewlyConfirmedFailures + other.pairsWithNewlyConfirmedFailures,
                newlyConfirmedFailingTests + other.newlyConfirmedFailingTests,
                selectedNewlyConfirmedFailures + other.selectedNewlyConfirmedFailures,
                skippedNewlyConfirmedFailures + other.skippedNewlyConfirmedFailures);
    }
}
