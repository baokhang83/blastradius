package io.github.baokhang83.blastradius.validator.build;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Distinguishes a genuine build failure (compile error, unresolvable dependency — the
 * build never got far enough to run any test) from an ordinary test failure (FR-009).
 *
 * <p>Heuristic: Surefire/Failsafe only ever write a {@code TEST-*.xml} report for a test
 * class it actually got to run. If the build failed <em>and</em> no such report exists
 * anywhere under the project, the failure happened before testing could even begin.
 */
public final class BuildFailureDetector {

    public boolean isBuildFailure(BuildResult result, Path projectDir) {
        return !result.succeeded() && !anyTestReportsExist(projectDir);
    }

    private static boolean anyTestReportsExist(Path projectDir) {
        try (Stream<Path> stream = Files.walk(projectDir)) {
            return stream.anyMatch(p -> {
                String name = p.getFileName().toString();
                return name.startsWith("TEST-") && name.endsWith(".xml");
            });
        } catch (IOException e) {
            throw new UncheckedIOException("failed to scan for test reports under " + projectDir, e);
        }
    }
}
