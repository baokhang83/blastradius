package io.github.baokhang83.blastradius.plugin.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    private final IndexApplicabilityResolver resolver = new IndexApplicabilityResolver();
    private final DependencyIndexWriter writer = new DependencyIndexWriter();

    @Test
    void missingIndexFileIsReportedAsMissing(@TempDir Path projectDir) {
        FixtureProjectBuilder.singleModule(projectDir).commit("initial");
        Path indexPath = projectDir.resolve(".blastradius/index.json");

        IndexApplicability applicability = resolver.resolve(indexPath, projectDir);

        assertEquals(IndexApplicability.Status.MISSING, applicability.status());
        assertNull(applicability.index());
    }

    @Test
    void unreadableIndexFileIsReportedAsUnreadable(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder.singleModule(projectDir).commit("initial");
        Path indexPath = projectDir.resolve(".blastradius/index.json");
        Files.createDirectories(indexPath.getParent());
        Files.writeString(indexPath, "{ this is not valid json ");

        IndexApplicability applicability = resolver.resolve(indexPath, projectDir);

        assertEquals(IndexApplicability.Status.UNREADABLE, applicability.status());
        assertNull(applicability.index());
    }

    @Test
    void unreachableAnchorCommitIsReportedAsUnreachable(@TempDir Path projectDir) {
        FixtureProjectBuilder.singleModule(projectDir).commit("initial");
        Path indexPath = projectDir.resolve(".blastradius/index.json");
        DependencyIndex index = new DependencyIndex(
                "0000000000000000000000000000000000000000", "2026-07-09T10:00:00Z", List.of());
        writer.write(indexPath, index);

        IndexApplicability applicability = resolver.resolve(indexPath, projectDir);

        assertEquals(IndexApplicability.Status.ANCHOR_UNREACHABLE, applicability.status());
        assertNull(applicability.index());
    }

    @Test
    void validIndexWithReachableAnchorIsApplicable(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        String anchorCommit = fixture.commit("initial");
        Path indexPath = projectDir.resolve(".blastradius/index.json");
        TestIdentity fooTest = new TestIdentity("com.example.FooTest", "checksAdd");
        DependencyIndex index = new DependencyIndex(anchorCommit, "2026-07-09T10:00:00Z",
                List.of(new TestDependencyEntry(fooTest, Set.of("com.example.Foo"))));
        writer.write(indexPath, index);

        IndexApplicability applicability = resolver.resolve(indexPath, projectDir);

        assertEquals(IndexApplicability.Status.APPLICABLE, applicability.status());
        assertNotNull(applicability.index());
        assertEquals(index, applicability.index());
    }
}
