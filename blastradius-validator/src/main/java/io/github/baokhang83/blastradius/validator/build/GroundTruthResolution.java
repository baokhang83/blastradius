package io.github.baokhang83.blastradius.validator.build;

import java.util.List;

/**
 * {@link GroundTruthResolver#resolve}'s full outcome: the {@link BuildResult} of the build
 * it ran to derive ground truth, alongside the per-test results parsed from it. Exposing
 * {@code initialBuild} lets a caller detect a build failure without running its own separate,
 * otherwise-identical probe build first.
 */
public record GroundTruthResolution(BuildResult initialBuild, List<GroundTruthResult> results) {}
