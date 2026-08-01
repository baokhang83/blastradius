package io.github.baokhang83.blastradius.validator.git;

/** Defines which direct parent edges a historical validator replay includes. */
public enum HistoryMode {
    /** Replay every direct parent edge for commits reachable from {@code HEAD}. */
    ALL_PARENTS,

    /** Replay only each reachable commit's first parent edge. */
    FIRST_PARENT
}
