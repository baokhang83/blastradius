package io.github.baokhang83.blastradius.validator.git;

import io.github.baokhang83.blastradius.core.git.ChangedFileClassifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Resolves a fixed, operator-configurable window of the most recent consecutive commit
 * pairs on a target project's default branch (FR-012). Only the ordered (base, head) SHA
 * pairs are resolved here; {@code changedFiles} classification is a separate concern
 * (see {@link ChangedFileClassifier}) applied later in the pipeline.
 */
public final class CommitWindowResolver {

    /**
     * @param repoPath   local working copy with full git history
     * @param windowSize number of most-recent commit pairs to resolve; a window of N
     *                   requires N+1 commits and yields N pairs. Returns fewer pairs if
     *                   the repository's history is shorter than requested.
     * @return pairs ordered oldest-to-newest (chronological replay order)
     */
    public List<CommitPair> resolveWindow(Path repoPath, int windowSize) {
        try (Git git = Git.open(repoPath.toFile())) {
            Repository repo = git.getRepository();
            ObjectId head = repo.resolve("HEAD");
            if (head == null) {
                return List.of();
            }

            List<RevCommit> newestFirst = new ArrayList<>();
            try (RevWalk walk = new RevWalk(repo)) {
                RevCommit start = walk.parseCommit(head);
                walk.markStart(start);
                int limit = windowSize + 1;
                for (RevCommit commit : walk) {
                    newestFirst.add(commit);
                    if (newestFirst.size() >= limit) {
                        break;
                    }
                }
            }

            List<RevCommit> oldestFirst = new ArrayList<>(newestFirst);
            Collections.reverse(oldestFirst);

            List<CommitPair> pairs = new ArrayList<>();
            for (int i = 0; i < oldestFirst.size() - 1; i++) {
                String base = oldestFirst.get(i).getName();
                String headSha = oldestFirst.get(i + 1).getName();
                pairs.add(CommitPair.analyzed(base, headSha, List.of()));
            }
            return pairs;
        } catch (Exception e) {
            throw new IllegalStateException("failed to resolve commit window for " + repoPath, e);
        }
    }
}
