package io.github.baokhang83.blastradius.validator.build;

/**
 * The lightweight result of a phase-1 build: whether it succeeded, and if not, why. It carries
 * <em>no</em> dependency records or ground-truth results — those live in the {@link BuildCache} on
 * disk and are loaded on demand during phase 2. This is the type {@link CommitBuildService} returns
 * so that phase 1's in-memory footprint stays bounded by the number of builds in flight rather than
 * growing with the whole commit window (the linear-heap growth that OOM'd a 300-commit run).
 *
 * <p>A successful outcome is only a marker: its full {@link CommitBuild} was written to the cache
 * under the same {@link CommitBuildService.BuildKey}. A failed outcome carries its reason and is
 * <em>not</em> cached — the referencing pair is excluded (FR-009), and a re-run rebuilds it.
 */
public record BuildOutcome(boolean failed, String failureReason) {

    private static final BuildOutcome OK = new BuildOutcome(false, null);

    public static BuildOutcome ok() {
        return OK;
    }

    public static BuildOutcome failed(String reason) {
        return new BuildOutcome(true, reason);
    }
}
