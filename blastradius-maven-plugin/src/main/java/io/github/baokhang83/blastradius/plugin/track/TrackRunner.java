package io.github.baokhang83.blastradius.plugin.track;

import io.github.baokhang83.blastradius.core.tracking.DependencyRecordReader;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex.TestDependencyEntry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Forks the target project's own {@code mvn test} as an independent subprocess, agent
 * attached via {@code JAVA_TOOL_OPTIONS}, to (re)build a {@link DependencyIndex} —
 * reusing {@code blastradius-core}'s proven tracking mechanism unchanged (unique-file-
 * per-JVM, merge-on-read, {@code TestIdentity.baselineKey()} normalization). Deliberately
 * never instruments the live, currently-running build being gated (research.md #1).
 */
public final class TrackRunner {

    private static final long TIMEOUT_MINUTES = 20;

    private final DependencyRecordReader recordReader = new DependencyRecordReader();

    public DependencyIndex track(Path projectDir, Path agentJar, String anchorCommit) {
        Path outputFile;
        try {
            outputFile = Files.createTempFile("blastradius-track-", ".json");
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create a temp file for the track run's output", e);
        }

        String agentOption = "-javaagent:" + agentJar.toAbsolutePath() + "=" + outputFile.toAbsolutePath();
        // -Dblastradius.trackChild=true: this subprocess runs against the same pom that
        // binds this very goal, so without the flag its own SelectMojo execution would
        // resolve TRACK again (same commit, same baseRef) and recurse without bound — see
        // SelectMojo.trackChild's javadoc.
        ProcessBuilder processBuilder = new ProcessBuilder(
                "mvn", "-B", "--no-transfer-progress", "-Dblastradius.trackChild=true", "clean", "test")
                .directory(projectDir.toFile())
                .redirectErrorStream(true);
        processBuilder.environment().merge("JAVA_TOOL_OPTIONS", agentOption, (existing, added) -> existing + " " + added);

        runToCompletion(processBuilder, projectDir);

        Map<TestIdentity, Map<String, String>> recorded = recordReader.readAll(outputFile);
        List<TestDependencyEntry> entries = recorded.entrySet().stream()
                .map(entry -> new TestDependencyEntry(entry.getKey(), entry.getValue().keySet()))
                .toList();
        return new DependencyIndex(anchorCommit, Instant.now().toString(), entries);
    }

    /**
     * Redirects the subprocess's output to a file rather than piping it, and waits for
     * the process with {@code waitFor(timeout, ...)} before ever touching that file —
     * piping + reading eagerly (the pattern {@code MavenBuildRunner}/{@code
     * DependencyTrackingIntegrationTest} use elsewhere in this codebase) blocks on {@code
     * readAllBytes()} until the child closes its stdout, which makes the timeout below
     * unreachable if a grandchild process (this method's own subprocess forks a nested
     * {@code mvn} run when it hits {@code SelectMojo}'s TRACK branch) keeps that pipe's
     * write end open. On timeout, the whole descendant tree is destroyed, not just the
     * immediate child, so no orphaned nested {@code mvn}/JVM process is left running.
     */
    private static void runToCompletion(ProcessBuilder processBuilder, Path projectDir) {
        Path outputFile;
        try {
            outputFile = Files.createTempFile("blastradius-track-run-", ".log");
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create a temp file for the track run's output", e);
        }
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.to(outputFile.toFile()));
        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                throw new IllegalStateException(
                        "track run timed out against " + projectDir + " after " + TIMEOUT_MINUTES + "m");
            }
            // A nonzero exit here typically means some tests failed, not that the build
            // itself is broken — the agent still wrote whatever it observed regardless of
            // pass/fail, so that data remains valid to track. A genuine build failure
            // (e.g. a compile error) instead surfaces naturally: no dependency record file
            // is ever produced, and DependencyRecordReader.readAll fails loudly for it.
        } catch (IOException e) {
            throw new UncheckedIOException("failed to invoke mvn test against " + projectDir, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for track run against " + projectDir, e);
        } finally {
            try {
                Files.deleteIfExists(outputFile);
            } catch (IOException ignored) {
                // Best-effort cleanup; a stray temp log is not worth failing the build over.
            }
        }
    }
}
