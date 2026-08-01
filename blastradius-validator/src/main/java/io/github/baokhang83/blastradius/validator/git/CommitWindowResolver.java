package io.github.baokhang83.blastradius.validator.git;

import io.github.baokhang83.blastradius.core.git.ChangedFileClassifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Resolves a fixed, operator-configurable window of direct parent-to-child edges in a target
 * project's reachable history (FR-012). Only the ordered (base, head) SHA pairs are resolved
 * here; {@code changedFiles} classification is a separate concern (see {@link
 * ChangedFileClassifier}) applied later in the pipeline.
 */
public final class CommitWindowResolver {

    /**
     * @param repoPath   local working copy with full git history
     * @param windowSize number of most-recent parent-child edges to resolve
     * @return pairs ordered oldest-to-newest (replay order), including every direct parent edge
     *         for a reachable merge commit
     */
    public List<CommitPair> resolveWindow(Path repoPath, int windowSize) {
        return resolveWindow(repoPath, windowSize, HistoryMode.ALL_PARENTS);
    }

    /**
     * Resolves a fixed window of direct parent-to-child history edges according to {@code mode}.
     *
     * @param repoPath   local working copy with full git history
     * @param windowSize number of most-recent parent-child edges to resolve
     * @param mode       whether every parent edge or only first-parent edges are included
     * @return pairs ordered oldest-to-newest (replay order)
     */
    public List<CommitPair> resolveWindow(Path repoPath, int windowSize, HistoryMode mode) {
        Objects.requireNonNull(mode, "mode");
        try (Git git = Git.open(repoPath.toFile())) {
            Repository repo = git.getRepository();
            ObjectId head = repo.resolve("HEAD");
            if (head == null) {
                return List.of();
            }

            List<CommitPair> newestFirst = new ArrayList<>();
            try (RevWalk walk = new RevWalk(repo)) {
                RevCommit start = walk.parseCommit(head);
                walk.markStart(start);
                for (RevCommit commit : walk) {
                    int parentCount = mode == HistoryMode.FIRST_PARENT
                            ? Math.min(1, commit.getParentCount())
                            : commit.getParentCount();
                    for (int parentIndex = 0; parentIndex < parentCount; parentIndex++) {
                        newestFirst.add(CommitPair.analyzed(
                                commit.getParent(parentIndex).getName(), commit.getName(), List.of()));
                        if (newestFirst.size() >= windowSize) {
                            break;
                        }
                    }
                    if (newestFirst.size() >= windowSize) {
                        break;
                    }
                }
            }

            java.util.Collections.reverse(newestFirst);
            return newestFirst;
        } catch (Exception e) {
            throw new IllegalStateException("failed to resolve commit window for " + repoPath, e);
        }
    }
}
