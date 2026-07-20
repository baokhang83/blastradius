package io.github.baokhang83.blastradius.validator.verdict;

import io.github.baokhang83.blastradius.validator.build.GroundTruthResult;
import io.github.baokhang83.blastradius.validator.build.Outcome;
import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.validator.git.CommitPair;
import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Compares selection decisions against confirmed ground truth for one commit pair,
 * producing a {@link WouldMissCase} for every confirmed-failed test that was excluded
 * (FR-004) — the mechanism that makes an unsound selection approach visible rather than
 * silently shipping a bug.
 */
public final class WouldMissComparator {

    public List<WouldMissCase> compare(
            CommitPair pair, List<SelectionDecision> decisions, List<GroundTruthResult> groundTruth) {
        Map<TestIdentity, SelectionDecision> decisionByTest =
                decisions.stream().collect(Collectors.toMap(SelectionDecision::test, Function.identity()));

        List<String> changedClasses = pair.changedFiles().stream()
                .flatMap(file -> file.candidateClassNames().stream())
                .toList();

        List<WouldMissCase> misses = new ArrayList<>();
        for (GroundTruthResult result : groundTruth) {
            if (result.outcome() != Outcome.CONFIRMED_FAILED) {
                continue;
            }
            SelectionDecision decision = decisionByTest.get(result.test());
            if (decision != null && decision.selected()) {
                continue;
            }
            String reason = decision == null ? "no selection decision recorded" : decision.reason().name();
            misses.add(new WouldMissCase(pair, result.test(), changedClasses, reason));
        }
        return misses;
    }
}
