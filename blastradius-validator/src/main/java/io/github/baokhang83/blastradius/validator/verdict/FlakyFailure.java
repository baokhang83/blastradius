package io.github.baokhang83.blastradius.validator.verdict;

import io.github.baokhang83.blastradius.validator.git.CommitPair;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;

/**
 * A test that failed once but passed on the single confirmation re-run (FR-013),
 * surfaced separately from would-miss cases so flakiness in the target project's own
 * suite is transparent rather than silently discarded (FR-014).
 */
public record FlakyFailure(CommitPair commitPair, TestIdentity test) {}
