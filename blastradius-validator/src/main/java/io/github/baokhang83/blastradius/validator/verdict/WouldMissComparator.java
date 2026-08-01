package io.github.baokhang83.blastradius.validator.verdict;

import io.github.baokhang83.blastradius.validator.build.GroundTruthResult;
import io.github.baokhang83.blastradius.validator.build.Outcome;
import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.validator.git.CommitPair;
import io.github.baokhang83.blastradius.validator.report.FailureCoverage;
import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Compares selection decisions against confirmed ground truth for one commit pair,
 * producing a {@link WouldMissCase} for every confirmed-failed test that was excluded
 * (FR-004) — the mechanism that makes an unsound selection approach visible rather than
 * silently shipping a bug.
 */
public final class WouldMissComparator {

    /**
     * @param baseGroundTruth the same commit pair's BASE commit ground truth, so a test
     *                        that was already {@link Outcome#CONFIRMED_FAILED} before this
     *                        pair's change — broken for reasons unrelated to it, e.g.
     *                        shenyu-admin's {@code RoleMapperTest#testSelectAll} asserting
     *                        an exact row count against a table schema.sql always seeds
     *                        with two default roles — is never reported as a would-miss.
     *                        Selection had nothing to catch: the failure predates the
     *                        diff, so no dependency edge could have flagged it.
     */
    public FailureComparison compare(
            CommitPair pair, List<SelectionDecision> decisions, List<GroundTruthResult> groundTruth,
            List<GroundTruthResult> baseGroundTruth) {
        Map<TestIdentity, SelectionDecision> decisionByTest =
                decisions.stream().collect(Collectors.toMap(SelectionDecision::test, Function.identity()));

        Set<TestIdentity> preExistingFailures = baseGroundTruth.stream()
                .filter(result -> result.outcome() == Outcome.CONFIRMED_FAILED)
                .map(GroundTruthResult::test)
                .collect(Collectors.toSet());

        List<String> changedClasses = pair.changedFiles().stream()
                .flatMap(file -> file.candidateClassNames().stream())
                .toList();

        List<WouldMissCase> misses = new ArrayList<>();
        int newlyConfirmedFailures = 0;
        int selectedNewlyConfirmedFailures = 0;
        for (GroundTruthResult result : groundTruth) {
            if (result.outcome() != Outcome.CONFIRMED_FAILED) {
                continue;
            }
            if (preExistingFailures.contains(result.test())) {
                continue;
            }
            newlyConfirmedFailures++;
            SelectionDecision decision = decisionByTest.get(result.test());
            if (decision != null && decision.selected()) {
                selectedNewlyConfirmedFailures++;
                continue;
            }
            String reason = decision == null ? "no selection decision recorded" : decision.reason().name();
            misses.add(new WouldMissCase(pair, result.test(), changedClasses, reason));
        }
        return new FailureComparison(misses, new FailureCoverage(
                newlyConfirmedFailures == 0 ? 0 : 1,
                newlyConfirmedFailures,
                selectedNewlyConfirmedFailures,
                misses.size()));
    }
}
