package io.github.baokhang83.blastradius.validator.selection;

import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.validator.verdict.FailureComparison;
import io.github.baokhang83.blastradius.validator.verdict.FlakyFailure;
import java.util.List;
import java.util.Objects;

/** Selection decisions and independently confirmed outcomes for one already-built Git edge. */
public record PairSelectionResult(
        List<SelectionDecision> decisions, FailureComparison failureComparison, List<FlakyFailure> flakyFailures) {

    public PairSelectionResult {
        Objects.requireNonNull(decisions, "decisions");
        Objects.requireNonNull(failureComparison, "failureComparison");
        Objects.requireNonNull(flakyFailures, "flakyFailures");
        decisions = List.copyOf(decisions);
        flakyFailures = List.copyOf(flakyFailures);
    }
}
