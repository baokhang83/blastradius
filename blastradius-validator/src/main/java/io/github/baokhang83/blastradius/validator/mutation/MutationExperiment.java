package io.github.baokhang83.blastradius.validator.mutation;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.List;
import java.util.Objects;

/** The auditable outcome of one real synthetic baseline-to-mutant Git edge. */
public record MutationExperiment(
        MutationCandidate mutation,
        String mutantCommit,
        MutationStatus status,
        String buildFailure,
        List<TestIdentity> killingTests,
        List<TestIdentity> selectedKillingTests,
        List<TestIdentity> skippedKillingTests,
        List<TestIdentity> flakyTests) {

    public MutationExperiment {
        Objects.requireNonNull(mutation, "mutation");
        Objects.requireNonNull(mutantCommit, "mutantCommit");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(killingTests, "killingTests");
        Objects.requireNonNull(selectedKillingTests, "selectedKillingTests");
        Objects.requireNonNull(skippedKillingTests, "skippedKillingTests");
        Objects.requireNonNull(flakyTests, "flakyTests");
        killingTests = List.copyOf(killingTests);
        selectedKillingTests = List.copyOf(selectedKillingTests);
        skippedKillingTests = List.copyOf(skippedKillingTests);
        flakyTests = List.copyOf(flakyTests);
        if (status == MutationStatus.UNBUILDABLE && (buildFailure == null || buildFailure.isBlank())) {
            throw new IllegalArgumentException("unbuildable mutant needs a buildFailure");
        }
        if (status != MutationStatus.UNBUILDABLE && buildFailure != null) {
            throw new IllegalArgumentException("buildFailure belongs only to an unbuildable mutant");
        }
        if (status == MutationStatus.KILLED && killingTests.isEmpty()) {
            throw new IllegalArgumentException("a killed mutant needs a confirmed killing test");
        }
        if (selectedKillingTests.size() + skippedKillingTests.size() != killingTests.size()) {
            throw new IllegalArgumentException("every killing test must be selected or skipped");
        }
    }
}
