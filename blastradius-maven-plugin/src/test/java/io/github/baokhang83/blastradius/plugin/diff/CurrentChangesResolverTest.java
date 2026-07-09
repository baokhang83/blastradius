package io.github.baokhang83.blastradius.plugin.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.git.FileKind;
import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CurrentChangesResolverTest {

    private final CurrentChangesResolver resolver = new CurrentChangesResolver();

    @Test
    void currentCommitEqualToResolvedBaseIsABaseRefBuild(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        String initial = fixture.commit("initial");

        // baseReference given directly as the current HEAD's own SHA: this build IS that commit.
        CurrentChanges changes = resolver.resolve(projectDir, initial);

        assertEquals(initial, changes.resolvedBaseCommit());
        assertEquals(initial, changes.currentCommit());
        assertTrue(changes.isBaseRefBuild());
        assertTrue(changes.changedFiles().isEmpty());
    }

    @Test
    void currentCommitAheadOfResolvedBaseIsNotABaseRefBuild(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        String base = fixture.commit("initial");
        fixture.writeClass("com.example.Foo",
                "package com.example; public class Foo { public int value() { return 1; } }");
        String head = fixture.commit("add Foo");

        CurrentChanges changes = resolver.resolve(projectDir, base);

        assertEquals(base, changes.resolvedBaseCommit());
        assertEquals(head, changes.currentCommit());
        assertFalse(changes.isBaseRefBuild());
        assertEquals(1, changes.changedFiles().size());
        assertEquals(FileKind.JAVA_SOURCE, changes.changedFiles().get(0).kind());
        assertEquals("com.example.Foo", changes.changedFiles().get(0).changedClassName());
    }

    @Test
    void baseReferenceIsRecordedVerbatim(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        String initial = fixture.commit("initial");

        CurrentChanges changes = resolver.resolve(projectDir, initial);

        assertEquals(initial, changes.baseReference());
    }
}
