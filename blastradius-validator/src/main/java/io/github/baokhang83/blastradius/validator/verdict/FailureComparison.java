package io.github.baokhang83.blastradius.validator.verdict;

import io.github.baokhang83.blastradius.validator.report.FailureCoverage;
import java.util.List;
import java.util.Objects;

/** The individual misses and aggregate coverage produced for one replayed commit edge. */
public record FailureComparison(List<WouldMissCase> wouldMissCases, FailureCoverage coverage) {

    public FailureComparison {
        Objects.requireNonNull(wouldMissCases, "wouldMissCases");
        Objects.requireNonNull(coverage, "coverage");
        wouldMissCases = List.copyOf(wouldMissCases);
        if (wouldMissCases.size() != coverage.skippedNewlyConfirmedFailures()) {
            throw new IllegalArgumentException("would-miss cases must equal skipped newly confirmed failures");
        }
    }
}
