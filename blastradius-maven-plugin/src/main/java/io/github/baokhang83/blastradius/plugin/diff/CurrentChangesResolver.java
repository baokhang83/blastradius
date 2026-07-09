package io.github.baokhang83.blastradius.plugin.diff;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.ChangedFileClassifier;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

/**
 * Resolves the current build's changes relative to a configured base reference
 * (data-model.md's {@link CurrentChanges}), reusing {@code blastradius-core}'s proven
 * {@link ChangedFileClassifier} for the actual diff — the same mechanism the shadow-mode
 * validator already relies on, applied live instead of to historical commit pairs.
 */
public final class CurrentChangesResolver {

    private final ChangedFileClassifier changedFileClassifier = new ChangedFileClassifier();

    public CurrentChanges resolve(Path projectDir, String baseReference) {
        ObjectId resolvedBaseId;
        ObjectId currentId;
        try (Git git = Git.open(projectDir.toFile())) {
            Repository repository = git.getRepository();
            resolvedBaseId = repository.resolve(baseReference);
            currentId = repository.resolve("HEAD");
        } catch (Exception e) {
            throw new IllegalStateException("failed to open git repository at " + projectDir, e);
        }
        if (resolvedBaseId == null) {
            throw new IllegalStateException(
                    "base reference \"" + baseReference + "\" does not resolve to a commit in " + projectDir);
        }
        if (currentId == null) {
            throw new IllegalStateException("HEAD does not resolve to a commit in " + projectDir);
        }

        String resolvedBaseCommit = resolvedBaseId.getName();
        String currentCommit = currentId.getName();
        boolean isBaseRefBuild = resolvedBaseCommit.equals(currentCommit);

        List<ChangedFile> changedFiles = isBaseRefBuild
                ? List.of()
                : changedFileClassifier.classify(projectDir, resolvedBaseCommit, currentCommit);

        return new CurrentChanges(baseReference, resolvedBaseCommit, currentCommit, isBaseRefBuild, changedFiles);
    }
}
