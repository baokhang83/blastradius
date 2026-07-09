package io.github.baokhang83.blastradius.validator.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.eclipse.jgit.api.Git;

/**
 * Materializes historical commits of a target project into an isolated scratch working
 * copy, without ever mutating the target repository's HEAD, current branch, or working
 * tree (plan.md's non-destructive-checkout constraint).
 *
 * <p>Implementation: the target repo is cloned once into a disposable scratch directory;
 * subsequent {@link #checkoutCommit(String)} calls check out different commits <em>within
 * that scratch clone</em>, which is safe to mutate repeatedly since it is never the
 * caller's real repository.
 */
public final class CommitCheckout implements AutoCloseable {

    private final Git scratchGit;
    private final Path scratchDir;

    private CommitCheckout(Git scratchGit, Path scratchDir) {
        this.scratchGit = scratchGit;
        this.scratchDir = scratchDir;
    }

    /**
     * Clones {@code targetRepoPath} once into a fresh subdirectory under
     * {@code scratchParentDir}. The target repository is only ever read from.
     */
    public static CommitCheckout forTargetProject(Path targetRepoPath, Path scratchParentDir) {
        try {
            Path scratchDir = Files.createTempDirectory(scratchParentDir, "blastradius-checkout-");
            Git clone = Git.cloneRepository()
                    .setURI(targetRepoPath.toUri().toString())
                    .setDirectory(scratchDir.toFile())
                    .call();
            return new CommitCheckout(clone, scratchDir);
        } catch (Exception e) {
            throw new IllegalStateException("failed to create scratch clone of " + targetRepoPath, e);
        }
    }

    /**
     * Checks out {@code commitSha} within the scratch clone (detached HEAD) and returns
     * the working directory now reflecting that commit's tree.
     */
    public Path checkoutCommit(String commitSha) {
        try {
            scratchGit.checkout().setName(commitSha).call();
            return scratchDir;
        } catch (Exception e) {
            throw new IllegalStateException("failed to checkout commit " + commitSha, e);
        }
    }

    /** Closes the scratch clone and deletes its directory. The target repo is untouched. */
    @Override
    public void close() {
        scratchGit.close();
        deleteRecursively(scratchDir);
    }

    private static void deleteRecursively(Path dir) {
        if (Files.notExists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // best-effort scratch cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort scratch cleanup
        }
    }
}
