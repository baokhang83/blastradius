package io.github.baokhang83.blastradius.plugin.index;

import io.github.baokhang83.blastradius.core.index.IndexStore;
import io.github.baokhang83.blastradius.core.index.CommitIndexKey;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
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

    public IndexApplicability resolve(
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

    /**
     * Resolves the exact comparison-base index first. When that exact key is absent, an older
     * index is usable only if its anchor is an ancestor of the commit being tested. Callers must
     * then expand their change set from that anchor through the tested commit.
     */
    public IndexApplicability resolve(
            IndexStore<DependencyIndex> store,
            String exactIndexKey,
            String indexPathKey,
            String expectedAnchorCommit,
            String currentCommit,
            Path projectDir) {
        IndexApplicability exact = resolve(store, exactIndexKey, expectedAnchorCommit, projectDir);
        if (exact.status() != IndexApplicability.Status.MISSING) {
            return exact;
        }

        try {
            return store.keys(parentPrefix(indexPathKey)).stream()
                    .map(key -> CommitIndexKey.commitFromKey(indexPathKey, key)
                            .flatMap(commit -> readCandidate(store, key, commit, currentCommit, projectDir)))
                    .flatMap(Optional::stream)
                .min(Comparator.comparingInt(Candidate::distanceFromHead))
                    .map(candidate -> IndexApplicability.staleBaseline(candidate.index()))
                    .orElseGet(IndexApplicability::missing);
        } catch (UncheckedIOException e) {
            return IndexApplicability.unreadable();
        }
    }

    private static Optional<Candidate> readCandidate(IndexStore<DependencyIndex> store, String key, String keyCommit,
            String currentCommit, Path projectDir) {
        DependencyIndex index = store.get(key).orElse(null);
        if (index == null || !index.hasCurrentFormat() || !index.anchorCommit().equals(keyCommit)) {
            return Optional.empty();
        }
        return distanceFromHead(index.anchorCommit(), currentCommit, projectDir)
                .map(distance -> new Candidate(index, distance));
    }

    private static String parentPrefix(String indexPathKey) {
        Path parent = Path.of(indexPathKey).normalize().getParent();
        return parent == null ? "" : parent.toString().replace('\\', '/');
    }

    private static Optional<Integer> distanceFromHead(String anchorCommit, String currentCommit, Path projectDir) {
        try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(projectDir.toFile())) {
            Repository repository = git.getRepository();
            ObjectId anchorId = repository.resolve(anchorCommit);
            ObjectId headId = repository.resolve(currentCommit);
            if (anchorId == null || headId == null) {
                return Optional.empty();
            }
            try (RevWalk walk = new RevWalk(repository)) {
                var anchor = walk.parseCommit(anchorId);
                var head = walk.parseCommit(headId);
                if (!walk.isMergedInto(anchor, head)) {
                    return Optional.empty();
                }
            }
            try (RevWalk walk = new RevWalk(repository)) {
                return Optional.of(org.eclipse.jgit.revwalk.RevWalkUtils.count(
                        walk, walk.parseCommit(headId), walk.parseCommit(anchorId)));
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private record Candidate(DependencyIndex index, int distanceFromHead) {}

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
