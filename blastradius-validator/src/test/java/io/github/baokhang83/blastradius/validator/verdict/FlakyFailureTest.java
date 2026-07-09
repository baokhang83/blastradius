package io.github.baokhang83.blastradius.validator.verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.baokhang83.blastradius.validator.git.CommitPair;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlakyFailureTest {

    @Test
    void carriesTheCommitPairAndTest() {
        CommitPair pair = CommitPair.analyzed("base", "head", List.of());
        TestIdentity test = new TestIdentity("com.example.FlakyTest", "sometimesFails");

        FlakyFailure flaky = new FlakyFailure(pair, test);

        assertEquals(pair, flaky.commitPair());
        assertEquals(test, flaky.test());
    }
}
