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
        ANCHOR_UNREACHABLE
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

    public static IndexApplicability anchorUnreachable() {
        return new IndexApplicability(Status.ANCHOR_UNREACHABLE, null);
    }
}
