package io.github.baokhang83.blastradius.validator.mutation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class MutationTextSummaryRendererTest {

    @Test
    void rendersTheSoundnessDenominatorAndSkippedKillingTest() {
        TestIdentity test = new TestIdentity("com.example.FlagTest", "detectsFalse");
        MutationExperiment experiment = new MutationExperiment(
                new MutationCandidate("src/main/java/com/example/Flag.java", "com.example.Flag",
                        MutationOperator.BOOLEAN_LITERAL, 0, "true", "false"),
                "mutant", MutationStatus.KILLED, null, List.of(test), List.of(), List.of(test), List.of());

        String text = new MutationTextSummaryRenderer().render(
                MutationReport.from("baseline", List.of(), List.of(experiment), 1, 0));

        assertTrue(text.contains("Verdict: FAIL"));
        assertTrue(text.contains("killing tests skipped: 1"));
        assertTrue(text.contains("skipped killing test: com.example.FlagTest#detectsFalse"));
    }
}
