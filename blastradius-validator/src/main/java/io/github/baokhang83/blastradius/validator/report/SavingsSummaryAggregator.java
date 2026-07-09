package io.github.baokhang83.blastradius.validator.report;

import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.selection.SelectionReason;
import java.util.List;

/** Aggregates {@link SelectionDecision}s across a completed run into a {@link SavingsSummary}. */
public final class SavingsSummaryAggregator {

    public SavingsSummary aggregate(List<SelectionDecision> allDecisions) {
        int total = allDecisions.size();
        int selected = (int) allDecisions.stream().filter(SelectionDecision::selected).count();
        int fallback = countByReason(allDecisions, SelectionReason.FALLBACK_NON_SOURCE_CHANGE);
        int dependencyMatched = countByReason(allDecisions, SelectionReason.DEPENDENCY_MATCH);
        int newOrModified = countByReason(allDecisions, SelectionReason.NEW_OR_MODIFIED_TEST);
        double proportionSkipped = total == 0 ? 0.0 : 1.0 - ((double) selected / total);

        return new SavingsSummary(total, selected, proportionSkipped, fallback, dependencyMatched, newOrModified);
    }

    private static int countByReason(List<SelectionDecision> decisions, SelectionReason reason) {
        return (int) decisions.stream().filter(d -> d.reason() == reason).count();
    }
}
