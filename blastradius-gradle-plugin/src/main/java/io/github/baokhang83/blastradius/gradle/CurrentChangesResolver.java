package io.github.baokhang83.blastradius.gradle;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.ChangedFileClassifier;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

final class CurrentChangesResolver {

    private final ChangedFileClassifier changedFileClassifier = new ChangedFileClassifier();

    CurrentChanges resolve(Path projectDir, String baseReference) {
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
            throw new IllegalStateException("base reference \"" + baseReference + "\" does not resolve to a commit");
        }
        if (currentId == null) {
            throw new IllegalStateException("HEAD does not resolve to a commit");
        }

        boolean baseRefBuild = resolvedBaseId.equals(currentId);
        List<ChangedFile> changedFiles = baseRefBuild
                ? List.of()
                : changedFileClassifier.classify(projectDir, resolvedBaseId.getName(), currentId.getName());
        return new CurrentChanges(currentId.getName(), baseRefBuild, changedFiles);
    }
}
