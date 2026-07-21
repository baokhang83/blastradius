package io.github.baokhang83.blastradius.gradle;

record IndexApplicability(Status status, DependencyIndex index) {

    enum Status {
        APPLICABLE,
        MISSING,
        UNREADABLE,
        FORMAT_VERSION_MISMATCH,
        ANCHOR_UNREACHABLE,
        ANCHOR_MISMATCH
    }

    static IndexApplicability applicable(DependencyIndex index) {
        return new IndexApplicability(Status.APPLICABLE, index);
    }

    static IndexApplicability missing() {
        return new IndexApplicability(Status.MISSING, null);
    }

    static IndexApplicability unreadable() {
        return new IndexApplicability(Status.UNREADABLE, null);
    }

    static IndexApplicability formatVersionMismatch() {
        return new IndexApplicability(Status.FORMAT_VERSION_MISMATCH, null);
    }

    static IndexApplicability anchorUnreachable() {
        return new IndexApplicability(Status.ANCHOR_UNREACHABLE, null);
    }

    static IndexApplicability anchorMismatch() {
        return new IndexApplicability(Status.ANCHOR_MISMATCH, null);
    }
}
