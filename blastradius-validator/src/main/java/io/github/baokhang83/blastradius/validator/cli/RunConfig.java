package io.github.baokhang83.blastradius.validator.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The operator-supplied input for a single validator run (FR-001, FR-012).
 *
 * @param projectPath          local git working copy of the target project
 * @param commitWindowSize     number of most-recent commits to analyze; operator-chosen,
 *                             no fixed default (FR-012)
 * @param reportOutputPath     file path to write the JSON {@code AnalysisReport} to
 * @param mavenParallelThreads value for the target project's own {@code mvn -T} reactor
 *                             parallelism, or {@code null} to build serially (the default)
 */
public record RunConfig(
        Path projectPath, int commitWindowSize, Path reportOutputPath, Integer mavenParallelThreads) {

    public RunConfig(Path projectPath, int commitWindowSize, Path reportOutputPath) {
        this(projectPath, commitWindowSize, reportOutputPath, null);
    }

    public RunConfig {
        Objects.requireNonNull(projectPath, "projectPath");
        if (!Files.isDirectory(projectPath)) {
            throw new IllegalArgumentException("projectPath does not exist or is not a directory: " + projectPath);
        }
        if (!Files.isDirectory(projectPath.resolve(".git"))) {
            throw new IllegalArgumentException("projectPath is not a git repository: " + projectPath);
        }
        if (commitWindowSize <= 0) {
            throw new IllegalArgumentException("commitWindowSize must be positive, got: " + commitWindowSize);
        }
        Objects.requireNonNull(reportOutputPath, "reportOutputPath");
        if (Files.isDirectory(reportOutputPath)) {
            throw new IllegalArgumentException("reportOutputPath must be a file path, not a directory: " + reportOutputPath);
        }
        if (mavenParallelThreads != null && mavenParallelThreads < 1) {
            throw new IllegalArgumentException(
                    "mavenParallelThreads must be positive, got: " + mavenParallelThreads);
        }
    }
}
