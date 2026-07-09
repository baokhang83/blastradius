package io.github.baokhang83.blastradius.validator.build;

/**
 * Ground-truth outcome for one test at one commit, after flaky-confirmation (FR-013).
 */
public enum Outcome {
    PASSED,
    /** Failed on both the original run and the single confirmation re-run. */
    CONFIRMED_FAILED,
    /** Failed originally but passed on the confirmation re-run — noise, not a would-miss. */
    FLAKY
}
