package io.github.baokhang83.blastradius.validator.git;

import java.util.Locale;

/** Defines which direct parent edges a historical validator replay includes. */
public enum HistoryMode {
    /** Replay every direct parent edge for commits reachable from {@code HEAD}. */
    ALL_PARENTS,

    /** Replay only each reachable commit's first parent edge. */
    FIRST_PARENT;

    /** Parses the stable kebab-case CLI spelling as well as the enum spelling. */
    public static HistoryMode fromCliValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("history mode must not be null");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
