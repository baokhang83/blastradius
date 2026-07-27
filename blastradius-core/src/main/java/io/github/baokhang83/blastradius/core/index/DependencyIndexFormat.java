package io.github.baokhang83.blastradius.core.index;

/** Version contract for persisted dependency-index JSON shared by the build integrations. */
public final class DependencyIndexFormat {

    /** The version emitted for every newly tracked dependency index. */
    public static final int CURRENT_VERSION = 2;

    private static final int LEGACY_UNVERSIONED_VERSION = 0;

    /**
     * The version a {@link #LEGACY_UNVERSIONED_VERSION} index migrates to: the last schema
     * before {@code ambientDependencies} existed. Fixed independently of {@link #CURRENT_VERSION}
     * — a legacy index genuinely has no ambient data, so migrating it straight to whatever
     * {@code CURRENT_VERSION} happens to be would falsely validate it against a schema it
     * doesn't match (an empty ambient set would look indistinguishable from "no ambient
     * classes exist", silently reintroducing the exact bug this format models).
     */
    public static final int PRE_AMBIENT_DEPENDENCIES_VERSION = 1;

    private DependencyIndexFormat() {
    }

    /** Maps the one known unversioned schema to its actual legacy version, never to {@link #CURRENT_VERSION}. */
    public static int migrateLegacyVersion(int version) {
        return version == LEGACY_UNVERSIONED_VERSION ? PRE_AMBIENT_DEPENDENCIES_VERSION : version;
    }

    /** Returns whether an index version is safe for the current selection implementation to use. */
    public static boolean isCurrentVersion(int version) {
        return version == CURRENT_VERSION;
    }
}
