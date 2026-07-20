package io.github.baokhang83.blastradius.plugin.index;

import io.github.baokhang83.blastradius.core.index.IndexStore;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Decides whether a persisted {@link DependencyIndex} can be used for the current build
 * (research.md #3): missing or unreadable index files, and an index whose {@code
 * anchorCommit} is no longer reachable in the project's git history (e.g. after a history
 * rewrite), all fall back rather than risk an unsound selection (FR-007).
 */
public final class IndexApplicabilityResolver {

    public IndexApplicability resolve(IndexStore<DependencyIndex> store, String indexKey, Path projectDir) {
        DependencyIndex index;
        try {
            index = store.get(indexKey).orElse(null);
        } catch (UncheckedIOException e) {
            return IndexApplicability.unreadable();
        }
        if (index == null) {
            return IndexApplicability.missing();
        }

        if (!anchorIsReachable(index.anchorCommit(), projectDir)) {
            return IndexApplicability.anchorUnreachable();
        }

        return IndexApplicability.applicable(index);
    }

    private static boolean anchorIsReachable(String anchorCommit, Path projectDir) {
        try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(projectDir.toFile())) {
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
