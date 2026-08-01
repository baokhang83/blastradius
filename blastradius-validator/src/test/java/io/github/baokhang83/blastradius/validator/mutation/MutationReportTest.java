package io.github.baokhang83.blastradius.validator.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.validator.verdict.Verdict;
import java.util.List;
import org.junit.jupiter.api.Test;

class MutationReportTest {

    @Test
    void skippedConfirmedKillingTestFailsTheMutationVerdictAndStaysVisibleInCoverage() {
        TestIdentity killingTest = new TestIdentity("com.example.FlagTest", "detectsFalse");
        MutationExperiment skippedKiller = new MutationExperiment(
                candidate(), "mutant-sha", MutationStatus.KILLED, null,
                List.of(killingTest), List.of(), List.of(killingTest), List.of());
        MutationExperiment unbuildable = new MutationExperiment(
                candidate(), "broken-sha", MutationStatus.UNBUILDABLE, "compilation failed",
                List.of(), List.of(), List.of(), List.of());

        MutationReport report = MutationReport.from("baseline-sha", List.of(), List.of(skippedKiller, unbuildable), 2, 0);

        assertEquals(Verdict.FAIL, report.verdict());
        assertEquals(2, report.coverage().generatedMutations());
        assertEquals(1, report.coverage().compilableMutants());
        assertEquals(1, report.coverage().testKilledMutants());
        assertEquals(1, report.coverage().mutationKillingTests());
        assertEquals(0, report.coverage().selectedKillingTests());
        assertEquals(1, report.coverage().skippedKillingTests());
        assertTrue(report.experiments().getFirst().skippedKillingTests().contains(killingTest));
    }

    @Test
    void unbuildableAndFlakyMutantsDoNotCreateASoundnessFailure() {
        MutationExperiment unbuildable = new MutationExperiment(
                candidate(), "broken-sha", MutationStatus.UNBUILDABLE, "compilation failed",
                List.of(), List.of(), List.of(), List.of());
        MutationExperiment flaky = new MutationExperiment(
                candidate(), "flaky-sha", MutationStatus.SURVIVED, null,
                List.of(), List.of(), List.of(), List.of(new TestIdentity("com.example.FlagTest", "flaky")));

        MutationReport report = MutationReport.from("baseline-sha", List.of(), List.of(unbuildable, flaky), 2, 0);

        assertEquals(Verdict.PASS, report.verdict());
        assertEquals(1, report.coverage().flakyMutantFailures());
        assertEquals(1, report.coverage().unbuildableMutants());
    }

    private MutationCandidate candidate() {
        return new MutationCandidate("src/main/java/com/example/Flag.java", "com.example.Flag",
                MutationOperator.BOOLEAN_LITERAL, 1, "true", "false");
    }
}
