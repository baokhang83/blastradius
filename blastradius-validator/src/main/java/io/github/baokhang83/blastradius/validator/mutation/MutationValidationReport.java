package io.github.baokhang83.blastradius.validator.mutation;

import io.github.baokhang83.blastradius.validator.verdict.Verdict;
import java.util.List;

/** Mutation evidence attached to one historical validator run. */
public record MutationValidationReport(
        Verdict verdict, MutationCoverage coverage, List<MutationExperiment> experiments) {

    public MutationValidationReport {
        experiments = List.copyOf(experiments);
    }

    public static MutationValidationReport from(
            List<MutationExperiment> experiments, int generatedMutations, int timeLimitSkippedMutations) {
        int unbuildable = (int) experiments.stream().filter(e -> e.status() == MutationStatus.UNBUILDABLE).count();
        int compilable = experiments.size() - unbuildable;
        int baselineClean = (int) experiments.stream()
                .filter(e -> e.status() != MutationStatus.UNBUILDABLE)
                .filter(e -> e.headBaselineFailingTests().isEmpty())
                .count();
        int killed = (int) experiments.stream().filter(e -> e.status() == MutationStatus.KILLED).count();
        int killingTests = experiments.stream().mapToInt(e -> e.killingTests().size()).sum();
        int selected = experiments.stream().mapToInt(e -> e.selectedKillingTests().size()).sum();
        int skipped = experiments.stream().mapToInt(e -> e.skippedKillingTests().size()).sum();
        int flaky = experiments.stream().mapToInt(e -> e.flakyTests().size()).sum();
        MutationCoverage coverage = new MutationCoverage(
                generatedMutations, experiments.size(), timeLimitSkippedMutations, unbuildable, compilable,
                baselineClean, killed, killingTests, selected, skipped, flaky);
        return new MutationValidationReport(skipped == 0 ? Verdict.PASS : Verdict.FAIL, coverage, experiments);
    }
}
