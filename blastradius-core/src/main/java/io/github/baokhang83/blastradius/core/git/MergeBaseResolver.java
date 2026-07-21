package io.github.baokhang83.blastradius.core.git;

import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

/** Resolves a build's HEAD, configured target, and their Git merge base. */
public final class MergeBaseResolver {

    public GitComparison resolve(Path repositoryDirectory, String baseReference) {
        try (Git git = Git.open(repositoryDirectory.toFile())) {
            Repository repository = git.getRepository();
            ObjectId baseId = repository.resolve(baseReference);
            ObjectId headId = repository.resolve("HEAD");
            if (baseId == null) {
                throw new IllegalStateException(
                        "base reference \"" + baseReference + "\" does not resolve to a commit in " + repositoryDirectory);
            }
            if (headId == null) {
                throw new IllegalStateException("HEAD does not resolve to a commit in " + repositoryDirectory);
            }

            Optional<String> comparisonBase = headId.equals(baseId)
                    ? Optional.of(baseId.getName())
                    : findMergeBase(repository, headId, baseId);
            return new GitComparison(headId.getName(), baseId.getName(), comparisonBase);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("failed to open git repository at " + repositoryDirectory, e);
        }
    }

    private static Optional<String> findMergeBase(Repository repository, ObjectId headId, ObjectId baseId)
            throws java.io.IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            walk.setRevFilter(RevFilter.MERGE_BASE);
            walk.markStart(walk.parseCommit(headId));
            walk.markStart(walk.parseCommit(baseId));
            RevCommit mergeBase = walk.next();
            return mergeBase == null ? Optional.empty() : Optional.of(mergeBase.getName());
        }
    }
}
