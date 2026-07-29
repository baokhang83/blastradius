package io.github.baokhang83.blastradius.core.reactor;

import java.util.Objects;

/**
 * One reactor module, identified by its Maven {@code artifactId} and its repo-relative
 * directory. The {@code relativePath} is what attributes a changed file or a test source to
 * this module; the {@code artifactId} is what inter-module dependency edges reference.
 *
 * @param artifactId   the module's Maven artifactId
 * @param relativePath the module directory, repo-relative, forward-slashed, no trailing slash
 *                     ({@code ""} for a module rooted at the repository root)
 */
public record ModuleId(String artifactId, String relativePath) {

    public ModuleId {
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(relativePath, "relativePath");
    }
}
