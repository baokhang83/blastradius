package io.github.baokhang83.blastradius.validator.mutation;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.validator.verdict.Verdict;
import java.util.List;
import java.util.Objects;

/** JSON source of truth for an opt-in mutation soundness run. */
public record MutationReport(
        Verdict verdict,
        String baselineCommit,
        List<TestIdentity> baselineFailingTests,
        MutationCoverage coverage,
        List<MutationExperiment> experiments) {

    public MutationReport {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(baselineCommit, "baselineCommit");
        Objects.requireNonNull(baselineFailingTests, "baselineFailingTests");
        Objects.requireNonNull(coverage, "coverage");
        Objects.requireNonNull(experiments, "experiments");
        baselineFailingTests = List.copyOf(baselineFailingTests);
        experiments = List.copyOf(experiments);
    }

    public static MutationReport from(
            String baselineCommit,
            List<TestIdentity> baselineFailingTests,
            List<MutationExperiment> experiments,
            int generatedMutations,
            int timeLimitSkippedMutations) {
        int unbuildable = (int) experiments.stream().filter(e -> e.status() == MutationStatus.UNBUILDABLE).count();
        int compilable = experiments.size() - unbuildable;
        int killed = (int) experiments.stream().filter(e -> e.status() == MutationStatus.KILLED).count();
        int killingTests = experiments.stream().mapToInt(e -> e.killingTests().size()).sum();
        int selected = experiments.stream().mapToInt(e -> e.selectedKillingTests().size()).sum();
        int skipped = experiments.stream().mapToInt(e -> e.skippedKillingTests().size()).sum();
        int flaky = experiments.stream().mapToInt(e -> e.flakyTests().size()).sum();
        MutationCoverage coverage = new MutationCoverage(
                generatedMutations, experiments.size(), timeLimitSkippedMutations, unbuildable, compilable,
                baselineFailingTests.isEmpty() ? compilable : 0, killed, killingTests, selected, skipped, flaky);
        return new MutationReport(skipped == 0 ? Verdict.PASS : Verdict.FAIL, baselineCommit,
                baselineFailingTests, coverage, experiments);
    }
}
