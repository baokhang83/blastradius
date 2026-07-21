package io.github.baokhang83.blastradius.core.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DependencyIndexFormatTest {

    @Test
    void migratesTheKnownUnversionedLegacySchemaToTheCurrentVersion() {
        assertEquals(DependencyIndexFormat.CURRENT_VERSION, DependencyIndexFormat.migrateLegacyVersion(0));
    }

    @Test
    void leavesAnUnsupportedVersionUntouchedForTheCallerToReject() {
        assertEquals(DependencyIndexFormat.CURRENT_VERSION + 1,
                DependencyIndexFormat.migrateLegacyVersion(DependencyIndexFormat.CURRENT_VERSION + 1));
    }

    @Test
    void recognizesOnlyTheCurrentVersionAsUsable() {
        assertTrue(DependencyIndexFormat.isCurrentVersion(DependencyIndexFormat.CURRENT_VERSION));
        assertFalse(DependencyIndexFormat.isCurrentVersion(DependencyIndexFormat.CURRENT_VERSION + 1));
    }
}
