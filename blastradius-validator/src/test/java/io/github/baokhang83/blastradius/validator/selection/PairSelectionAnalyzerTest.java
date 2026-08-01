package io.github.baokhang83.blastradius.validator.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.FileKind;
import io.github.baokhang83.blastradius.core.tracking.DependencyRecordSet;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.validator.build.GroundTruthResult;
import io.github.baokhang83.blastradius.validator.build.Outcome;
import io.github.baokhang83.blastradius.validator.git.CommitPair;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PairSelectionAnalyzerTest {

    private final PairSelectionAnalyzer analyzer = new PairSelectionAnalyzer();

    @Test
    void usesTheSameDependencyDecisionForAConfirmedNewFailure() {
        TestIdentity test = new TestIdentity("com.example.FlagTest", "detectsFalse");
        List<ChangedFile> changedFiles = List.of(
                new ChangedFile("src/main/java/com/example/Flag.java", FileKind.JAVA_SOURCE, "com.example.Flag"));
        DependencyRecordSet baseline = new DependencyRecordSet(
                Map.of(test, Map.of("com.example.Flag", "checksum")), Set.of());
        CommitPair edge = CommitPair.analyzed("base", "mutant", changedFiles);

        PairSelectionResult result = analyzer.analyze(
                edge, baseline,
                List.of(new GroundTruthResult(test, Outcome.PASSED)),
                List.of(new GroundTruthResult(test, Outcome.CONFIRMED_FAILED)), null);

        assertTrue(result.decisions().getFirst().selected());
        assertEquals(1, result.failureComparison().coverage().newlyConfirmedFailingTests());
        assertEquals(1, result.failureComparison().coverage().selectedNewlyConfirmedFailures());
        assertTrue(result.failureComparison().wouldMissCases().isEmpty());
    }
}
