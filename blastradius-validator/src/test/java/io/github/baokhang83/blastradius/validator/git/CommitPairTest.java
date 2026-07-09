package io.github.baokhang83.blastradius.validator.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.FileKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommitPairTest {

    @Test
    void analyzedFactoryDefaultsToAnalyzedStatusWithNoExclusionReason() {
        ChangedFile file = new ChangedFile("src/main/java/com/example/Foo.java",
                FileKind.JAVA_SOURCE, "com.example.Foo");
        CommitPair pair = CommitPair.analyzed("base123", "head456", List.of(file));

        assertEquals(PairStatus.ANALYZED, pair.status());
        assertNull(pair.exclusionReason());
        assertEquals("base123", pair.baseCommit());
        assertEquals("head456", pair.headCommit());
        assertEquals(1, pair.changedFiles().size());
    }

    @Test
    void excludedFactorySetsExcludedStatusWithReason() {
        CommitPair pair = CommitPair.excluded("base123", "head456", "build failed");

        assertEquals(PairStatus.EXCLUDED, pair.status());
        assertEquals("build failed", pair.exclusionReason());
        assertTrue(pair.changedFiles().isEmpty());
    }

    @Test
    void changedFilesListIsImmutable() {
        ChangedFile file = new ChangedFile("Foo.java", FileKind.JAVA_SOURCE, "Foo");
        CommitPair pair = CommitPair.analyzed("a", "b", List.of(file));
        assertThrows(UnsupportedOperationException.class, () -> pair.changedFiles().add(file));
    }

    @Test
    void nonSourceChangedFileHasNullChangedClassName() {
        ChangedFile file = new ChangedFile("pom.xml", FileKind.NON_SOURCE, null);
        assertNull(file.changedClassName());
    }

    @Test
    void javaSourceChangedFileRequiresChangedClassName() {
        assertThrows(NullPointerException.class,
                () -> new ChangedFile("Foo.java", FileKind.JAVA_SOURCE, null));
    }

    @Test
    void sameFieldsAreEqual() {
        CommitPair a = CommitPair.analyzed("x", "y", List.of());
        CommitPair b = CommitPair.analyzed("x", "y", List.of());
        assertEquals(a, b);
    }
}
