package io.github.baokhang83.blastradius.validator.verdict;

import java.util.List;

/** PASS iff zero would-miss cases occurred across the whole analyzed run (FR-005). */
public final class VerdictCalculator {

    public Verdict calculate(List<WouldMissCase> allWouldMissCases) {
        return allWouldMissCases.isEmpty() ? Verdict.PASS : Verdict.FAIL;
    }
}
