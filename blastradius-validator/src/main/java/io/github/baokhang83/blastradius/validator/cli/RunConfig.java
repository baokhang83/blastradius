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
 * @param fastGroundTruth      opt-in, default {@code false}. When {@code true}, every commit
 *                             in the window gets one canonical, agent-attached build cached
 *                             and reused across every pair that references it — trading the
 *                             ground-truth build's independence from the tracking agent for
 *                             fewer builds. Never implied by anything else; must be requested
 *                             explicitly (constitution §III — see design.md for the full
 *                             reasoning).
 * @param buildConcurrency     how many commit builds run at once, each in its own isolated
 *                             working copy (see CommitBuildService / CheckoutPool). Defaults
 *                             to {@code 1} — today's exact serial behavior, so existing
 *                             callers are unchanged. Higher values fill the cores a single
 *                             {@code -T} reactor build leaves idle on its critical path; on an
 *                             8-core box a modest {@code -T} times this &asymp; core count
 *                             (e.g. {@code -T 2} &times; {@code build-concurrency 4}). Purely
 *                             a scheduling lever: it never changes which builds run or how
 *                             they are compared, so it is orthogonal to §III.
 */
public record RunConfig(
        Path projectPath, int commitWindowSize, Path reportOutputPath, Integer mavenParallelThreads,
        boolean fastGroundTruth, int buildConcurrency) {

    public RunConfig(Path projectPath, int commitWindowSize, Path reportOutputPath) {
        this(projectPath, commitWindowSize, reportOutputPath, null, false, 1);
    }

    public RunConfig(Path projectPath, int commitWindowSize, Path reportOutputPath, Integer mavenParallelThreads) {
        this(projectPath, commitWindowSize, reportOutputPath, mavenParallelThreads, false, 1);
    }

    public RunConfig(
            Path projectPath, int commitWindowSize, Path reportOutputPath, Integer mavenParallelThreads,
            boolean fastGroundTruth) {
        this(projectPath, commitWindowSize, reportOutputPath, mavenParallelThreads, fastGroundTruth, 1);
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
        if (buildConcurrency < 1) {
            throw new IllegalArgumentException("buildConcurrency must be positive, got: " + buildConcurrency);
        }
    }
}
