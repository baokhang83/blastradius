package io.github.baokhang83.blastradius.gradle;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

final class IndexApplicabilityResolver {

    private final DependencyIndexReader reader = new DependencyIndexReader();

    IndexApplicability resolve(Path indexPath, Path projectDir) {
        if (Files.notExists(indexPath)) {
            return IndexApplicability.missing();
        }

        DependencyIndex index;
        try {
            index = reader.read(indexPath);
        } catch (UncheckedIOException e) {
            return IndexApplicability.unreadable();
        }

        return anchorIsReachable(index.anchorCommit(), projectDir)
                ? IndexApplicability.applicable(index)
                : IndexApplicability.anchorUnreachable();
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
