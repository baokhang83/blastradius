package io.github.baokhang83.blastradius.core.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CommitIndexKeyTest {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    @Test
    void insertsTheResolvedCommitBeforeTheConfiguredIndexFileName() {
        assertEquals(
                ".blastradius/0123456789abcdef0123456789abcdef01234567/index.json",
                CommitIndexKey.forCommit(".blastradius/index.json", COMMIT));
    }

    @Test
    void retainsTheConfiguredParentAndFileName() {
        assertEquals(
                "cache/blastradius/0123456789abcdef0123456789abcdef01234567/dependencies.json",
                CommitIndexKey.forCommit("cache/blastradius/dependencies.json", COMMIT));
    }

    @Test
    void rejectsAnAbbreviatedCommit() {
        assertThrows(IllegalArgumentException.class, () -> CommitIndexKey.forCommit(".blastradius/index.json", "0123456"));
    }
}
