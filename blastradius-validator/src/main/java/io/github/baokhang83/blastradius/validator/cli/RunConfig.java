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
 * @param buildTimeoutMinutes  how long a single {@code mvn} build may run before it is killed and
 *                             reported as timed out. Defaults to {@code 5}. A large reactor
 *                             (e.g. apache/shenyu) legitimately runs a full {@code clean test}
 *                             past five minutes, so this must be raised there or every build is
 *                             killed mid-flight and misclassified as a build failure.
 * @param skipBuildExtras      opt-in, default {@code false}. When {@code true}, every target build
 *                             switches off coverage/lint/license/resource-bundling plugins
 *                             (JaCoCo, Checkstyle, apache-rat, maven-remote-resources) that run
 *                             during {@code clean test} but change neither which tests run nor
 *                             whether they pass. Pure per-build wall-clock savings; soundness-
 *                             neutral (constitution §III), unlike scoping the reactor.
 */
public record RunConfig(
        Path projectPath, int commitWindowSize, Path reportOutputPath, Integer mavenParallelThreads,
        boolean fastGroundTruth, int buildConcurrency, long buildTimeoutMinutes, boolean skipBuildExtras) {

    /** Default build timeout in minutes when the operator doesn't override it. */
    public static final long DEFAULT_BUILD_TIMEOUT_MINUTES = 5;

    public RunConfig(Path projectPath, int commitWindowSize, Path reportOutputPath) {
        this(projectPath, commitWindowSize, reportOutputPath, null, false, 1, DEFAULT_BUILD_TIMEOUT_MINUTES, false);
    }

    public RunConfig(Path projectPath, int commitWindowSize, Path reportOutputPath, Integer mavenParallelThreads) {
        this(projectPath, commitWindowSize, reportOutputPath, mavenParallelThreads, false, 1,
                DEFAULT_BUILD_TIMEOUT_MINUTES, false);
    }

    public RunConfig(
            Path projectPath, int commitWindowSize, Path reportOutputPath, Integer mavenParallelThreads,
            boolean fastGroundTruth) {
        this(projectPath, commitWindowSize, reportOutputPath, mavenParallelThreads, fastGroundTruth, 1,
                DEFAULT_BUILD_TIMEOUT_MINUTES, false);
    }

    public RunConfig(
            Path projectPath, int commitWindowSize, Path reportOutputPath, Integer mavenParallelThreads,
            boolean fastGroundTruth, int buildConcurrency) {
        this(projectPath, commitWindowSize, reportOutputPath, mavenParallelThreads, fastGroundTruth,
                buildConcurrency, DEFAULT_BUILD_TIMEOUT_MINUTES, false);
    }

    public RunConfig(
            Path projectPath, int commitWindowSize, Path reportOutputPath, Integer mavenParallelThreads,
            boolean fastGroundTruth, int buildConcurrency, long buildTimeoutMinutes) {
        this(projectPath, commitWindowSize, reportOutputPath, mavenParallelThreads, fastGroundTruth,
                buildConcurrency, buildTimeoutMinutes, false);
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
        if (buildTimeoutMinutes < 1) {
            throw new IllegalArgumentException("buildTimeoutMinutes must be positive, got: " + buildTimeoutMinutes);
        }
    }
}
