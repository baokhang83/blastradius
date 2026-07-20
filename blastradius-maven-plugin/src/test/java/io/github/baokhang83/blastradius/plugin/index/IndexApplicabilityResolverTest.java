package io.github.baokhang83.blastradius.plugin.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.baokhang83.blastradius.core.index.FileIndexStore;
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
        FixtureProjectBuilder.singleModule(projectDir).commit("initial");

        IndexApplicability applicability = resolver.resolve(store(projectDir), INDEX_KEY, projectDir);

        assertEquals(IndexApplicability.Status.MISSING, applicability.status());
        assertNull(applicability.index());
    }

    @Test
    void unreadableIndexFileIsReportedAsUnreadable(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder.singleModule(projectDir).commit("initial");
        Path indexPath = projectDir.resolve(INDEX_KEY);
        Files.createDirectories(indexPath.getParent());
        Files.writeString(indexPath, "{ this is not valid json ");

        IndexApplicability applicability = resolver.resolve(store(projectDir), INDEX_KEY, projectDir);

        assertEquals(IndexApplicability.Status.UNREADABLE, applicability.status());
        assertNull(applicability.index());
    }

    @Test
    void unreachableAnchorCommitIsReportedAsUnreachable(@TempDir Path projectDir) {
        FixtureProjectBuilder.singleModule(projectDir).commit("initial");
        DependencyIndex index = new DependencyIndex(
                "0000000000000000000000000000000000000000", "2026-07-09T10:00:00Z", List.of());
        store(projectDir).put(INDEX_KEY, index);

        IndexApplicability applicability = resolver.resolve(store(projectDir), INDEX_KEY, projectDir);

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

        IndexApplicability applicability = resolver.resolve(store(projectDir), INDEX_KEY, projectDir);

        assertEquals(IndexApplicability.Status.APPLICABLE, applicability.status());
        assertNotNull(applicability.index());
        assertEquals(index, applicability.index());
    }

    private static IndexStore<DependencyIndex> store(Path projectDir) {
        return new FileIndexStore<>(projectDir, DependencyIndex.class);
    }
}
