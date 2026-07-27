package io.github.baokhang83.blastradius.plugin.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.baokhang83.blastradius.core.index.FileIndexStore;
import io.github.baokhang83.blastradius.core.index.CommitIndexKey;
import io.github.baokhang83.blastradius.core.index.DependencyIndexFormat;
import io.github.baokhang83.blastradius.core.index.IndexStore;
import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex.TestDependencyEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexApplicabilityResolverTest {

    private static final String INDEX_KEY = ".blastradius/index.json";

    private final IndexApplicabilityResolver resolver = new IndexApplicabilityResolver();

    @Test
    void missingIndexFileIsReportedAsMissing(@TempDir Path projectDir) {
        String baseCommit = FixtureProjectBuilder.singleModule(projectDir).commit("initial");

        IndexApplicability applicability = resolver.resolve(store(projectDir), INDEX_KEY, baseCommit, projectDir);

        assertEquals(IndexApplicability.Status.MISSING, applicability.status());
        assertNull(applicability.index());
    }

    @Test
    void unreadableIndexFileIsReportedAsUnreadable(@TempDir Path projectDir) throws Exception {
        String baseCommit = FixtureProjectBuilder.singleModule(projectDir).commit("initial");
        Path indexPath = projectDir.resolve(INDEX_KEY);
        Files.createDirectories(indexPath.getParent());
        Files.writeString(indexPath, "{ this is not valid json ");

        IndexApplicability applicability = resolver.resolve(store(projectDir), INDEX_KEY, baseCommit, projectDir);

        assertEquals(IndexApplicability.Status.UNREADABLE, applicability.status());
        assertNull(applicability.index());
    }

    @Test
    void unreachableAnchorCommitIsReportedAsUnreachable(@TempDir Path projectDir) {
        String baseCommit = FixtureProjectBuilder.singleModule(projectDir).commit("initial");
        DependencyIndex index = new DependencyIndex(
                "0000000000000000000000000000000000000000", "2026-07-09T10:00:00Z", List.of());
        store(projectDir).put(INDEX_KEY, index);

        IndexApplicability applicability = resolver.resolve(store(projectDir), INDEX_KEY, baseCommit, projectDir);

        assertEquals(IndexApplicability.Status.ANCHOR_UNREACHABLE, applicability.status());
        assertNull(applicability.index());
    }

    @Test
    void validIndexWithReachableAnchorIsApplicable(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        String anchorCommit = fixture.commit("initial");
        TestIdentity fooTest = new TestIdentity("com.example.FooTest", "checksAdd");
        DependencyIndex index = new DependencyIndex(anchorCommit, "2026-07-09T10:00:00Z",
                List.of(new TestDependencyEntry(fooTest, Set.of("com.example.Foo"))));
        store(projectDir).put(INDEX_KEY, index);

        IndexApplicability applicability = resolver.resolve(store(projectDir), INDEX_KEY, anchorCommit, projectDir);

        assertEquals(IndexApplicability.Status.APPLICABLE, applicability.status());
        assertNotNull(applicability.index());
        assertEquals(index, applicability.index());
    }

    @Test
    void reachableIndexForAnotherBaselineIsReportedAsAnchorMismatch(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        String expectedBaseCommit = fixture.commit("initial");
        fixture.writeClass("com.example.Newer", "package com.example; class Newer {}");
        String otherCommit = fixture.commit("later baseline");
        store(projectDir).put(INDEX_KEY, new DependencyIndex(otherCommit, "2026-07-09T10:00:00Z", List.of()));

        IndexApplicability applicability = resolver.resolve(store(projectDir), INDEX_KEY, expectedBaseCommit, projectDir);

        assertEquals(IndexApplicability.Status.ANCHOR_MISMATCH, applicability.status());
        assertNull(applicability.index());
    }

    @Test
    void usesNearestReachableAncestorWhenTheExactBaselineIsMissing(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        String oldestAnchor = fixture.commit("oldest baseline");
        fixture.writeClass("com.example.Stale", "package com.example; class Stale {}");
        String staleAnchor = fixture.commit("stale baseline");
        fixture.writeClass("com.example.Expected", "package com.example; class Expected {}");
        String expectedBase = fixture.commit("expected base without an index");
        fixture.writeClass("com.example.Feature", "package com.example; class Feature {}");
        String head = fixture.commit("feature change");

        DependencyIndex olderIndex = new DependencyIndex(oldestAnchor, "2026-07-09T10:00:00Z", List.of());
        DependencyIndex staleIndex = new DependencyIndex(staleAnchor, "2026-07-09T10:00:00Z", List.of());
        IndexStore<DependencyIndex> store = store(projectDir);
        store.put(CommitIndexKey.forCommit(INDEX_KEY, oldestAnchor), olderIndex);
        store.put(CommitIndexKey.forCommit(INDEX_KEY, staleAnchor), staleIndex);

        IndexApplicability applicability = resolver.resolve(
                store,
                CommitIndexKey.forCommit(INDEX_KEY, expectedBase),
                INDEX_KEY,
                expectedBase,
                head,
                projectDir);

        assertEquals(IndexApplicability.Status.STALE_BASELINE, applicability.status());
        assertEquals(staleIndex, applicability.index());
    }

    @Test
    void staleBaselineFallbackReportsFormatMismatchNotMissingWhenOnlyCandidateIsOutdated(
            @TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        String oldAnchor = fixture.commit("last baseline before a format bump");
        fixture.writeClass("com.example.Feature", "package com.example; class Feature {}");
        String expectedBase = fixture.commit("main advances past the format bump");
        fixture.writeClass("com.example.Changed", "package com.example; class Changed {}");
        String head = fixture.commit("feature change");

        DependencyIndex outdatedIndex = new DependencyIndex(DependencyIndexFormat.CURRENT_VERSION - 1,
                oldAnchor, "2026-07-09T10:00:00Z", List.of(), Set.of());
        IndexStore<DependencyIndex> store = store(projectDir);
        store.put(CommitIndexKey.forCommit(INDEX_KEY, oldAnchor), outdatedIndex);

        IndexApplicability applicability = resolver.resolve(
                store,
                CommitIndexKey.forCommit(INDEX_KEY, expectedBase),
                INDEX_KEY,
                expectedBase,
                head,
                projectDir);

        assertEquals(IndexApplicability.Status.FORMAT_VERSION_MISMATCH, applicability.status());
        assertNull(applicability.index());
    }

    @Test
    void unsupportedIndexFormatIsReportedAsAMismatch(@TempDir Path projectDir) {
        String anchorCommit = FixtureProjectBuilder.singleModule(projectDir).commit("initial");
        store(projectDir).put(INDEX_KEY, new DependencyIndex(
                DependencyIndexFormat.CURRENT_VERSION + 1, anchorCommit, "2026-07-09T10:00:00Z", List.of(), Set.of()));

        IndexApplicability applicability = resolver.resolve(store(projectDir), INDEX_KEY, anchorCommit, projectDir);

        assertEquals(IndexApplicability.Status.FORMAT_VERSION_MISMATCH, applicability.status());
        assertNull(applicability.index());
    }

    private static IndexStore<DependencyIndex> store(Path projectDir) {
        return new FileIndexStore<>(projectDir, DependencyIndex.class);
    }
}
