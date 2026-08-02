package io.github.baokhang83.blastradius.validator.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.validator.git.CommitPair;
import io.github.baokhang83.blastradius.validator.verdict.Verdict;
import java.util.List;
import org.junit.jupiter.api.Test;

class MutationValidationReportTest {

    private static final CommitPair PAIR = CommitPair.analyzed("base", "head", List.of());
    private static final TestIdentity KILLER = new TestIdentity("org.app.KillerTest", null);
    private static final TestIdentity OTHER_KILLER = new TestIdentity("org.app.OtherKillerTest", null);

    private static MutationCandidate candidate() {
        return new MutationCandidate("m/src/main/java/org/app/Foo.java", "org.app.Foo",
                MutationOperator.BOOLEAN_LITERAL, 10, "true", "false");
    }

    private static MutationExperiment experiment(
            MutationCandidateOrigin origin, MutationStatus status, String buildFailure,
            List<TestIdentity> killing, List<TestIdentity> selected, List<TestIdentity> skipped) {
        return new MutationExperiment(PAIR, candidate(), origin, "mutant1", status, buildFailure,
                List.of(), killing, selected, skipped, List.of());
    }

    @Test
    void inconclusiveWhenNoMutantWasKilled() {
        MutationExperiment survived = experiment(MutationCandidateOrigin.DIFF_TARGETED,
                MutationStatus.SURVIVED, null, List.of(), List.of(), List.of());
        MutationExperiment unbuildable = experiment(MutationCandidateOrigin.DIFF_TARGETED,
                MutationStatus.UNBUILDABLE, "compile error", List.of(), List.of(), List.of());

        MutationValidationReport report =
                MutationValidationReport.from(List.of(survived, unbuildable), 2, 0);

        assertEquals(Verdict.INCONCLUSIVE, report.verdict());
        assertEquals(0, report.coverage().diffTargeted().killingTests());
        assertEquals(0, report.coverage().wholeTreeFallback().killingTests());
    }

    @Test
    void inconclusiveWhenNoExperimentsRanAtAll() {
        MutationValidationReport report = MutationValidationReport.from(List.of(), 0, 0);

        assertEquals(Verdict.INCONCLUSIVE, report.verdict());
    }

    @Test
    void passWhenEveryKillingTestWasSelected() {
        MutationExperiment killed = experiment(MutationCandidateOrigin.DIFF_TARGETED,
                MutationStatus.KILLED, null, List.of(KILLER), List.of(KILLER), List.of());

        MutationValidationReport report = MutationValidationReport.from(List.of(killed), 1, 0);

        assertEquals(Verdict.PASS, report.verdict());
    }

    @Test
    void failWhenAKillingTestWasSkipped() {
        MutationExperiment killed = experiment(MutationCandidateOrigin.DIFF_TARGETED,
                MutationStatus.KILLED, null, List.of(KILLER), List.of(), List.of(KILLER));

        MutationValidationReport report = MutationValidationReport.from(List.of(killed), 1, 0);

        assertEquals(Verdict.FAIL, report.verdict());
    }

    @Test
    void splitsCoverageBetweenDiffTargetedAndWholeTreeFallback() {
        MutationExperiment targetedKill = experiment(MutationCandidateOrigin.DIFF_TARGETED,
                MutationStatus.KILLED, null, List.of(KILLER), List.of(KILLER), List.of());
        MutationExperiment fallbackKillWithSkip = experiment(MutationCandidateOrigin.WHOLE_TREE_FALLBACK,
                MutationStatus.KILLED, null, List.of(OTHER_KILLER), List.of(), List.of(OTHER_KILLER));

        MutationValidationReport report =
                MutationValidationReport.from(List.of(targetedKill, fallbackKillWithSkip), 2, 0);

        MutationOriginCoverage targeted = report.coverage().diffTargeted();
        assertEquals(1, targeted.killingTests());
        assertEquals(1, targeted.selectedKillingTests());
        assertEquals(0, targeted.skippedKillingTests());

        MutationOriginCoverage fallback = report.coverage().wholeTreeFallback();
        assertEquals(1, fallback.killingTests());
        assertEquals(0, fallback.selectedKillingTests());
        assertEquals(1, fallback.skippedKillingTests());

        // The skip only happened in the fallback pool, but the overall verdict still reports it —
        // provenance is extra context, not a reason to hide a real would-miss.
        assertEquals(Verdict.FAIL, report.verdict());
    }
}
