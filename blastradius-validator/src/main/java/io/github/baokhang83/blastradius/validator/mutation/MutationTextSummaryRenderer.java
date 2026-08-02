package io.github.baokhang83.blastradius.validator.mutation;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;

/** Human-readable rendering of the mutation JSON report, not a second source of truth. */
public final class MutationTextSummaryRenderer {

    public String render(MutationReport report) {
        MutationCoverage coverage = report.coverage();
        StringBuilder text = new StringBuilder("Verdict: ").append(report.verdict()).append('\n');
        text.append("Baseline: ").append(report.baselineCommit()).append('\n');
        text.append("Skipped test classes: ").append(report.skippedTests().size()).append('\n');
        for (String skippedTest : report.skippedTests()) {
            text.append("  - ").append(skippedTest).append('\n');
        }
        text.append("Mutation coverage:\n");
        text.append("  - generated: ").append(coverage.generatedMutations()).append('\n');
        text.append("  - attempted: ").append(coverage.attemptedMutations()).append('\n');
        text.append("  - skipped by time limit: ").append(coverage.timeLimitSkippedMutations()).append('\n');
        text.append("  - unbuildable: ").append(coverage.unbuildableMutants()).append('\n');
        text.append("  - compilable: ").append(coverage.compilableMutants()).append('\n');
        text.append("  - baseline-clean: ").append(coverage.baselineCleanMutants()).append('\n');
        text.append("  - test-killed: ").append(coverage.testKilledMutants()).append('\n');
        text.append("  - killing tests: ").append(coverage.mutationKillingTests()).append('\n');
        text.append("  - killing tests selected: ").append(coverage.selectedKillingTests()).append('\n');
        text.append("  - killing tests skipped: ").append(coverage.skippedKillingTests()).append('\n');
        text.append("  - flaky mutant failures: ").append(coverage.flakyMutantFailures()).append('\n');
        text.append("Baseline failures: ").append(report.baselineFailingTests().size()).append('\n');
        for (TestIdentity test : report.baselineFailingTests()) {
            text.append("  - ").append(label(test)).append('\n');
        }
        for (MutationExperiment experiment : report.experiments()) {
            text.append("Mutation ").append(experiment.mutantCommit()).append(" ")
                    .append(experiment.mutation().className()).append(" ")
                    .append(experiment.mutation().original()).append(" -> ")
                    .append(experiment.mutation().replacement()).append(": ")
                    .append(experiment.status()).append('\n');
            for (TestIdentity test : experiment.skippedKillingTests()) {
                text.append("  - skipped killing test: ").append(label(test)).append('\n');
            }
        }
        return text.toString();
    }

    private static String label(TestIdentity test) {
        return test.methodName() == null ? test.className() : test.className() + "#" + test.methodName();
    }
}
