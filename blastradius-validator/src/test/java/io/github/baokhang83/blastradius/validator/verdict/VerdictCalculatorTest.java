package io.github.baokhang83.blastradius.validator.verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.baokhang83.blastradius.validator.git.CommitPair;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerdictCalculatorTest {

    private final VerdictCalculator calculator = new VerdictCalculator();

    @Test
    void noWouldMissCasesYieldsPass() {
        assertEquals(Verdict.PASS, calculator.calculate(List.of()));
    }

    @Test
    void anyWouldMissCaseYieldsFail() {
        CommitPair pair = CommitPair.analyzed("base", "head", List.of());
        WouldMissCase miss = new WouldMissCase(pair, new TestIdentity("com.example.FooTest", "checksAdd"),
                List.of("com.example.Foo"), "NO_MATCH");

        assertEquals(Verdict.FAIL, calculator.calculate(List.of(miss)));
    }
}
