package io.github.baokhang83.blastradius.validator.build;

import io.github.baokhang83.blastradius.core.tracking.DependencyRecordSet;
import java.util.List;

/**
 * One commit's build outcome, captured once and reused by every pair in the window that
 * references that commit in that role. Produced concurrently by {@link CommitBuildService}
 * up front (phase 1), then consumed serially during selection + comparison (phase 2).
 *
 * <p>A {@code failed} build carries only its {@code failureReason}; the pair(s) that
 * reference it are excluded (FR-009) rather than aborting the run. A successful build
 * carries the {@link DependencyRecordSet} (present only when the build was agent-attached —
 * {@code null} for an agent-free ground-truth build) and the per-test ground-truth results.
 */
public record CommitBuild(
        boolean failed,
        String failureReason,
        DependencyRecordSet dependencyRecordSet,
        List<GroundTruthResult> groundTruth) {

    /** A successful agent-attached build, carrying both dependencies and ground truth. */
    public static CommitBuild succeeded(DependencyRecordSet dependencyRecordSet, List<GroundTruthResult> groundTruth) {
        return new CommitBuild(false, null, dependencyRecordSet, groundTruth);
    }

    /** A build that never produced usable results; {@code reason} explains the exclusion. */
    public static CommitBuild failed(String reason) {
        return new CommitBuild(true, reason, null, List.of());
    }
}
