package io.github.baokhang83.blastradius.plugin.index;

/**
 * Whether a persisted {@link DependencyIndex} can be used for the current build, computed
 * fresh on every {@code blastradius:select} invocation (research.md #3). Only {@code
 * APPLICABLE} carries a non-null {@link #index()}; every other status triggers fallback
 * (FR-007) rather than guessing.
 */
public record IndexApplicability(Status status, DependencyIndex index) {

    public enum Status {
        APPLICABLE,
        MISSING,
        UNREADABLE,
        FORMAT_VERSION_MISMATCH,
        ANCHOR_UNREACHABLE,
        ANCHOR_MISMATCH,
        MERGE_BASE_UNAVAILABLE,
        /**
         * Not a property of the index itself — set only when an otherwise-{@code
         * APPLICABLE} index's {@code SELECT} computation hits an unexpected internal
         * fault (tasks.md T050, spec.md's "the plugin's own selection computation
         * encounters an internal error" Edge Case), so the build still falls back to a
         * full, unfiltered run instead of crashing or silently skipping tests.
         */
        INTERNAL_ERROR
    }

    public static IndexApplicability applicable(DependencyIndex index) {
        return new IndexApplicability(Status.APPLICABLE, index);
    }

    public static IndexApplicability missing() {
        return new IndexApplicability(Status.MISSING, null);
    }

    public static IndexApplicability unreadable() {
        return new IndexApplicability(Status.UNREADABLE, null);
    }

    public static IndexApplicability formatVersionMismatch() {
        return new IndexApplicability(Status.FORMAT_VERSION_MISMATCH, null);
    }

    public static IndexApplicability anchorUnreachable() {
        return new IndexApplicability(Status.ANCHOR_UNREACHABLE, null);
    }

    public static IndexApplicability anchorMismatch() {
        return new IndexApplicability(Status.ANCHOR_MISMATCH, null);
    }

    public static IndexApplicability mergeBaseUnavailable() {
        return new IndexApplicability(Status.MERGE_BASE_UNAVAILABLE, null);
    }

    public static IndexApplicability internalError() {
        return new IndexApplicability(Status.INTERNAL_ERROR, null);
    }
}
