package io.github.baokhang83.blastradius.validator.verdict;

import io.github.baokhang83.blastradius.validator.git.CommitPair;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.List;

/**
 * A confirmed-failed test (FR-013) that selection excluded — the core soundness signal
 * this whole validator exists to surface (FR-004).
 *
 * @param commitPair       the commit pair the failure occurred at
 * @param test             the excluded, confirmed-failed test
 * @param changedClasses   what changed in this commit pair
 * @param selectionReason  why the test was not selected
 */
public record WouldMissCase(
        CommitPair commitPair, TestIdentity test, List<String> changedClasses, String selectionReason) {}
