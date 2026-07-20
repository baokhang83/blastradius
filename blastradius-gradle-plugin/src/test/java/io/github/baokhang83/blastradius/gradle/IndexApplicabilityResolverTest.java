package io.github.baokhang83.blastradius.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.baokhang83.blastradius.core.index.FileIndexStore;
import io.github.baokhang83.blastradius.core.index.IndexStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexApplicabilityResolverTest {

    private static final String INDEX_KEY = ".blastradius/index.json";

    @Test
    void matchingReachableAnchorIsApplicable(@TempDir Path projectDir) throws Exception {
        String baseCommit = commit(projectDir, "initial");
        DependencyIndex index = new DependencyIndex(baseCommit, Instant.now().toString(), List.of());
        store(projectDir).put(INDEX_KEY, index);

        IndexApplicability applicability = new IndexApplicabilityResolver()
                .resolve(store(projectDir), INDEX_KEY, baseCommit, projectDir);

        assertEquals(IndexApplicability.Status.APPLICABLE, applicability.status());
        assertEquals(index, applicability.index());
    }

    @Test
    void reachableAnchorForAnotherBaselineIsReportedAsMismatch(@TempDir Path projectDir) throws Exception {
        String expectedBaseCommit = commit(projectDir, "initial");
        String otherCommit = commit(projectDir, "later baseline");
        store(projectDir).put(INDEX_KEY, new DependencyIndex(otherCommit, Instant.now().toString(), List.of()));

        IndexApplicability applicability = new IndexApplicabilityResolver()
                .resolve(store(projectDir), INDEX_KEY, expectedBaseCommit, projectDir);

        assertEquals(IndexApplicability.Status.ANCHOR_MISMATCH, applicability.status());
    }

    private static String commit(Path projectDir, String message) throws Exception {
        Files.writeString(projectDir.resolve(message.replace(' ', '-') + ".txt"), message);
        try (Git git = Files.exists(projectDir.resolve(".git"))
                ? Git.open(projectDir.toFile())
                : Git.init().setDirectory(projectDir.toFile()).call()) {
            git.add().addFilepattern(".").call();
            PersonIdent author = new PersonIdent("test", "test@example.com");
            return git.commit().setMessage(message).setAuthor(author).setCommitter(author).call().getName();
        }
    }

    private static IndexStore<DependencyIndex> store(Path projectDir) {
        return new FileIndexStore<>(projectDir, DependencyIndex.class);
    }
}
