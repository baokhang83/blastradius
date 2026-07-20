package io.github.baokhang83.blastradius.gradle;

import java.nio.file.Path;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

/** Resolves the root-relative shared index path without allowing it to escape the build root. */
final class IndexPathResolver {

    Path resolve(Project project, BlastradiusExtension extension) {
        Path root = project.getRootDir().toPath().normalize();
        Path resolved = root.resolve(extension.getIndexPath().get()).normalize();
        if (!resolved.startsWith(root)) {
            throw new GradleException("indexPath resolves outside the root project: " + resolved);
        }
        return resolved;
    }
}
