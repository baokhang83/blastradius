package io.github.baokhang83.blastradius.validator.build;

/**
 * The outcome of one {@link MavenBuildRunner} invocation.
 *
 * @param exitCode the subprocess's exit code; {@code 0} conventionally means success
 * @param output   combined stdout/stderr, for diagnostics and build-failure detection
 */
public record BuildResult(int exitCode, String output) {

    public boolean succeeded() {
        return exitCode == 0;
    }
}
