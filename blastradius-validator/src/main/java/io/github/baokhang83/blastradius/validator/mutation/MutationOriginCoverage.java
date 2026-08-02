package io.github.baokhang83.blastradius.validator.mutation;

/** The killing-test slice of {@link MutationCoverage} attributable to one {@link MutationCandidateOrigin}. */
public record MutationOriginCoverage(int killingTests, int selectedKillingTests, int skippedKillingTests) {

    public MutationOriginCoverage {
        if (killingTests < 0 || selectedKillingTests < 0 || skippedKillingTests < 0) {
            throw new IllegalArgumentException("mutation origin coverage counts must not be negative");
        }
        if (selectedKillingTests + skippedKillingTests != killingTests) {
            throw new IllegalArgumentException("selected and skipped killing tests must sum to all killing tests");
        }
    }
}
