package io.github.baokhang83.blastradius.core.selection;

/** Why a test was (or was not) selected for a given commit pair. */
public enum SelectionReason {
    /** The test's tracked dependencies intersect a changed class (FR-002). */
    DEPENDENCY_MATCH,
    /** A non-Java-source change forced selection of every test (FR-006). */
    FALLBACK_NON_SOURCE_CHANGE,
    /** The test is new or its own file was modified (FR-007). */
    NEW_OR_MODIFIED_TEST,
    /** Not selected — no changed class intersects its tracked dependencies. */
    NO_MATCH
}
