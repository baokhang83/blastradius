package io.github.baokhang83.blastradius.validator.selection;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.reactor.ReactorScope;
import io.github.baokhang83.blastradius.core.selection.NewOrModifiedTestSelector;
import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.selection.SelectionEngine;
import io.github.baokhang83.blastradius.core.tracking.DependencyRecordSet;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.validator.build.GroundTruthResult;
import io.github.baokhang83.blastradius.validator.build.Outcome;
import io.github.baokhang83.blastradius.validator.git.CommitPair;
import io.github.baokhang83.blastradius.validator.verdict.FlakyFailure;
import io.github.baokhang83.blastradius.validator.verdict.WouldMissComparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The selection-and-comparison half of validator replay, shared by historical and synthetic
 * mutation edges once both sides have established full-suite outcomes.
 */
public final class PairSelectionAnalyzer {

    private final SelectionEngine selectionEngine = new SelectionEngine();
    private final NewOrModifiedTestSelector newOrModifiedTestSelector = new NewOrModifiedTestSelector();
    private final WouldMissComparator wouldMissComparator = new WouldMissComparator();

    public PairSelectionResult analyze(
            CommitPair edge,
            DependencyRecordSet baselineDependencies,
            List<GroundTruthResult> baselineOutcomes,
            List<GroundTruthResult> headOutcomes,
            ReactorScope reactorScope) {
        Map<TestIdentity, Set<String>> testDependencies = baselineDependencies.tests().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().baselineKey(),
                        entry -> entry.getValue().keySet(),
                        (left, right) -> {
                            Set<String> union = new java.util.HashSet<>(left);
                            union.addAll(right);
                            return union;
                        }));
        Set<String> changedClassNames = edge.changedFiles().stream()
                .flatMap(file -> file.candidateClassNames().stream())
                .collect(Collectors.toUnmodifiableSet());
        Set<TestIdentity> allTests = headOutcomes.stream()
                .map(GroundTruthResult::test)
                .collect(Collectors.toSet());
        Set<TestIdentity> newOrModifiedTests = allTests.stream()
                .filter(test -> newOrModifiedTestSelector.appliesTo(
                        test, !testDependencies.containsKey(test.baselineKey()), changedClassNames))
                .collect(Collectors.toSet());
        List<SelectionDecision> decisions = selectionEngine.selectAll(
                allTests, testDependencies, newOrModifiedTests, edge.changedFiles(),
                baselineDependencies.ambientDependencies(), reactorScope);
        List<FlakyFailure> flakyFailures = headOutcomes.stream()
                .filter(result -> result.outcome() == Outcome.FLAKY)
                .map(result -> new FlakyFailure(edge, result.test()))
                .toList();
        return new PairSelectionResult(
                decisions, wouldMissComparator.compare(edge, decisions, headOutcomes, baselineOutcomes), flakyFailures);
    }
}
