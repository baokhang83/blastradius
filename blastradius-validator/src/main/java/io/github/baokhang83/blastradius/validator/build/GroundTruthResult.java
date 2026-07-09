package io.github.baokhang83.blastradius.validator.build;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;

/** The authoritative, confirmed outcome for one test at one commit pair's head commit. */
public record GroundTruthResult(TestIdentity test, Outcome outcome) {}
