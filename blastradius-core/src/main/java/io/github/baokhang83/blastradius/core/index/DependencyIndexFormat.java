package io.github.baokhang83.blastradius.core.index;

/** Version contract for persisted dependency-index JSON shared by the build integrations. */
public final class DependencyIndexFormat {

    /** The version emitted for every newly tracked dependency index. */
    public static final int CURRENT_VERSION = 1;

    private static final int LEGACY_UNVERSIONED_VERSION = 0;

    private DependencyIndexFormat() {
    }

    /** Maps the one known unversioned schema to the current version without accepting future schemas. */
    public static int migrateLegacyVersion(int version) {
        return version == LEGACY_UNVERSIONED_VERSION ? CURRENT_VERSION : version;
    }

    /** Returns whether an index version is safe for the current selection implementation to use. */
    public static boolean isCurrentVersion(int version) {
        return version == CURRENT_VERSION;
    }
}
