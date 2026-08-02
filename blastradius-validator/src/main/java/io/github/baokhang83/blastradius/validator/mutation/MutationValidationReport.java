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
        MutationOriginCoverage diffTargeted = originCoverage(experiments, MutationCandidateOrigin.DIFF_TARGETED);
        MutationOriginCoverage wholeTreeFallback =
                originCoverage(experiments, MutationCandidateOrigin.WHOLE_TREE_FALLBACK);
        MutationCoverage coverage = new MutationCoverage(
                generatedMutations, experiments.size(), timeLimitSkippedMutations, unbuildable, compilable,
                baselineClean, killed, killingTests, selected, skipped, flaky, diffTargeted, wholeTreeFallback);
        // killed == 0 means every attempted mutant either survived or failed to build: selection
        // was never actually put to the test, so a bare PASS here would claim confirmation this
        // run never earned (§III) — distinct from skipped == 0 with killed > 0, a real PASS.
        Verdict verdict = killed == 0 ? Verdict.INCONCLUSIVE : skipped == 0 ? Verdict.PASS : Verdict.FAIL;
        return new MutationValidationReport(verdict, coverage, experiments);
    }

    private static MutationOriginCoverage originCoverage(
            List<MutationExperiment> experiments, MutationCandidateOrigin origin) {
        List<MutationExperiment> fromOrigin = experiments.stream().filter(e -> e.origin() == origin).toList();
        int killingTests = fromOrigin.stream().mapToInt(e -> e.killingTests().size()).sum();
        int selected = fromOrigin.stream().mapToInt(e -> e.selectedKillingTests().size()).sum();
        int skipped = fromOrigin.stream().mapToInt(e -> e.skippedKillingTests().size()).sum();
        return new MutationOriginCoverage(killingTests, selected, skipped);
    }
}
