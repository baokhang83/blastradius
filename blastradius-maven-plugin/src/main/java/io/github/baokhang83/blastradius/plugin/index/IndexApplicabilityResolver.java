package io.github.baokhang83.blastradius.plugin.index;

import io.github.baokhang83.blastradius.core.index.IndexStore;
import io.github.baokhang83.blastradius.core.index.CommitIndexKey;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
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
            List<KeyEvaluation> evaluations = store.keys(parentPrefix(indexPathKey)).stream()
                    .map(key -> CommitIndexKey.commitFromKey(indexPathKey, key)
                            .map(commit -> evaluate(store, key, commit, currentCommit, projectDir)))
                    .flatMap(Optional::stream)
                    .toList();

            return evaluations.stream()
                    .flatMap(evaluation -> evaluation.candidate().stream())
                    .min(Comparator.comparingInt(Candidate::distanceFromHead))
                    .map(candidate -> IndexApplicability.staleBaseline(candidate.index()))
                    .orElseGet(() -> evaluations.stream().anyMatch(KeyEvaluation::formatIncompatible)
                            ? IndexApplicability.formatVersionMismatch()
                            : IndexApplicability.missing());
        } catch (UncheckedIOException e) {
            return IndexApplicability.unreadable();
        }
    }

    /**
     * Evaluates one enumerated candidate key, distinguishing "this key just doesn't
     * qualify" (empty candidate) from *why* — a format-incompatible index still means an
     * index was found, which is more informative than the enumeration coming up
     * completely empty (Constitution §V, Explainability): a stale baseline whose only
     * candidate predates a format-version bump on {@code main} should say so, not report
     * bare {@code MISSING} as if nothing had ever been cached.
     */
    private static KeyEvaluation evaluate(IndexStore<DependencyIndex> store, String key, String keyCommit,
            String currentCommit, Path projectDir) {
        DependencyIndex index = store.get(key).orElse(null);
        if (index == null || !index.anchorCommit().equals(keyCommit)) {
            return new KeyEvaluation(Optional.empty(), false);
        }
        if (!index.hasCurrentFormat()) {
            return new KeyEvaluation(Optional.empty(), true);
        }
        Optional<Candidate> candidate = distanceFromHead(index.anchorCommit(), currentCommit, projectDir)
                .map(distance -> new Candidate(index, distance));
        return new KeyEvaluation(candidate, false);
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

    /** A qualifying {@code candidate}, or a non-qualifying key flagged {@code formatIncompatible}. */
    private record KeyEvaluation(Optional<Candidate> candidate, boolean formatIncompatible) {}

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
