package io.github.baokhang83.blastradius.validator.mutation;

/** The full denominator behind a bounded mutation-validation verdict. */
public record MutationCoverage(
        int generatedMutations,
        int attemptedMutations,
        int timeLimitSkippedMutations,
        int unbuildableMutants,
        int compilableMutants,
        int baselineCleanMutants,
        int testKilledMutants,
        int mutationKillingTests,
        int selectedKillingTests,
        int skippedKillingTests,
        int flakyMutantFailures) {

    public MutationCoverage {
        if (generatedMutations < 0 || attemptedMutations < 0 || timeLimitSkippedMutations < 0
                || unbuildableMutants < 0 || compilableMutants < 0 || baselineCleanMutants < 0
                || testKilledMutants < 0 || mutationKillingTests < 0 || selectedKillingTests < 0
                || skippedKillingTests < 0 || flakyMutantFailures < 0) {
            throw new IllegalArgumentException("mutation coverage counts must not be negative");
        }
        if (selectedKillingTests + skippedKillingTests != mutationKillingTests) {
            throw new IllegalArgumentException("selected and skipped killing tests must sum to all killing tests");
        }
    }
}
