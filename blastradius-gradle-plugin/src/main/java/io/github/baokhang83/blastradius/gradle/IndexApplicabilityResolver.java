package io.github.baokhang83.blastradius.gradle;

import io.github.baokhang83.blastradius.core.index.IndexStore;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

final class IndexApplicabilityResolver {

    IndexApplicability resolve(
            IndexStore<DependencyIndex> store, String indexKey, String expectedAnchorCommit, Path projectDir) {
        DependencyIndex index;
        try {
            index = store.get(indexKey).orElse(null);
        } catch (UncheckedIOException e) {
            return IndexApplicability.unreadable();
        }
        if (index == null) {
            return IndexApplicability.missing();
        }
        if (!index.hasCurrentFormat()) {
            return IndexApplicability.formatVersionMismatch();
        }

        if (!anchorIsReachable(index.anchorCommit(), projectDir)) {
            return IndexApplicability.anchorUnreachable();
        }
        if (!index.anchorCommit().equals(expectedAnchorCommit)) {
            return IndexApplicability.anchorMismatch();
        }
        return IndexApplicability.applicable(index);
    }

    private static boolean anchorIsReachable(String anchorCommit, Path projectDir) {
        try (Git git = Git.open(projectDir.toFile())) {
            Repository repository = git.getRepository();
            ObjectId anchorId = repository.resolve(anchorCommit);
            if (anchorId == null) {
                return false;
            }
            try (RevWalk revWalk = new RevWalk(repository)) {
                revWalk.parseCommit(anchorId);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
