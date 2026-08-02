package io.github.baokhang83.blastradius.validator.mutation;

import io.github.baokhang83.blastradius.validator.build.SkippedTests;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Operator-supplied bounds for one opt-in mutation soundness run. */
public record MutationConfig(
        Path projectPath,
        Path reportOutputPath,
        String classFilter,
        int maxMutationClasses,
        int maxMutations,
        long timeLimitMinutes,
        Integer mavenParallelThreads,
        long buildTimeoutMinutes,
        boolean skipBuildExtras,
        SkippedTests skippedTests) {

    public static final int DEFAULT_MAX_CLASSES = 10;
    public static final int DEFAULT_MAX_MUTATIONS = 20;
    public static final long DEFAULT_TIME_LIMIT_MINUTES = 60;
    public static final long DEFAULT_BUILD_TIMEOUT_MINUTES = 5;

    public MutationConfig(Path projectPath, Path reportOutputPath) {
        this(projectPath, reportOutputPath, null, DEFAULT_MAX_CLASSES, DEFAULT_MAX_MUTATIONS,
                DEFAULT_TIME_LIMIT_MINUTES, null, DEFAULT_BUILD_TIMEOUT_MINUTES, false, SkippedTests.none());
    }

    /** Compatibility constructor for callers that do not use explicit test exclusions. */
    public MutationConfig(
            Path projectPath, Path reportOutputPath, String classFilter, int maxMutationClasses,
            int maxMutations, long timeLimitMinutes, Integer mavenParallelThreads,
            long buildTimeoutMinutes, boolean skipBuildExtras) {
        this(projectPath, reportOutputPath, classFilter, maxMutationClasses, maxMutations,
                timeLimitMinutes, mavenParallelThreads, buildTimeoutMinutes, skipBuildExtras, SkippedTests.none());
    }

    public MutationConfig {
        Objects.requireNonNull(projectPath, "projectPath");
        Objects.requireNonNull(reportOutputPath, "reportOutputPath");
        Objects.requireNonNull(skippedTests, "skippedTests");
        if (!Files.isDirectory(projectPath) || !Files.isDirectory(projectPath.resolve(".git"))) {
            throw new IllegalArgumentException("projectPath must be a local git repository: " + projectPath);
        }
        if (Files.isDirectory(reportOutputPath)) {
            throw new IllegalArgumentException("reportOutputPath must be a file path, not a directory: " + reportOutputPath);
        }
        if (maxMutationClasses < 1 || maxMutations < 1 || timeLimitMinutes < 1 || buildTimeoutMinutes < 1) {
            throw new IllegalArgumentException("mutation limits must be positive");
        }
        if (mavenParallelThreads != null && mavenParallelThreads < 1) {
            throw new IllegalArgumentException("mavenParallelThreads must be positive");
        }
    }
}
