package io.github.baokhang83.blastradius.core.selection;

/** Why a test was (or was not) selected for a given commit pair. */
public enum SelectionReason {
    /** The test's tracked dependencies intersect a changed class (FR-002). */
    DEPENDENCY_MATCH,
    /** A non-Java-source change forced selection of every test (FR-006). */
    FALLBACK_NON_SOURCE_CHANGE,
    /**
     * A non-Java-source change was scoped to the reactor: this test lives in a module that
     * changed, or in one that (transitively) depends on a changed module, so it was
     * selected — while tests in unaffected modules were spared. The safe narrowing of
     * {@link #FALLBACK_NON_SOURCE_CHANGE} when a reactor graph is available and the change
     * is not reactor-wide (root/parent pom).
     */
    FALLBACK_NON_SOURCE_DEPENDENT_MODULE,
    /**
     * A changed class was loaded before any test's tracking window opened in some fork
     * (an "ambient" dependency), so no per-test dependency data can be trusted for it —
     * every test was conservatively selected instead of risking a silent {@code NO_MATCH}.
     */
    FALLBACK_AMBIENT_DEPENDENCY,
    /** The test is new or its own file was modified (FR-007). */
    NEW_OR_MODIFIED_TEST,
    /** Not selected — no changed class intersects its tracked dependencies. */
    NO_MATCH
}
