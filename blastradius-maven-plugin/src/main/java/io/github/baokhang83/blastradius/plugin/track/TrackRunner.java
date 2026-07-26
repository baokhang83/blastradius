package io.github.baokhang83.blastradius.plugin.track;

import io.github.baokhang83.blastradius.core.tracking.DependencyRecordReader;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex.TestDependencyEntry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Forks the target project's own {@code mvn test} as an independent subprocess, with the agent
 * attached to Surefire via {@code argLine}, to (re)build a {@link DependencyIndex} —
 * reusing {@code blastradius-core}'s proven tracking mechanism unchanged (unique-file-
 * per-JVM, merge-on-read, {@code TestIdentity.baselineKey()} normalization). Deliberately
 * never instruments the live, currently-running build being gated (research.md #1).
 */
public final class TrackRunner {

    private static final long TIMEOUT_MINUTES = 20;
    private static final int FAILURE_OUTPUT_TAIL_BYTES = 12 * 1024;

    private final DependencyRecordReader recordReader = new DependencyRecordReader();

    public DependencyIndex track(Path projectDir, Path agentJar, String anchorCommit) {
        Path outputFile;
        try {
            outputFile = Files.createTempFile("blastradius-track-", ".json");
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create a temp file for the track run's output", e);
        }

        String agentOption = "-javaagent:" + agentJar.toAbsolutePath() + "=" + outputFile.toAbsolutePath();
        String existingArgLine = System.getProperty("argLine");
        String trackedArgLine = existingArgLine == null || existingArgLine.isBlank()
                ? agentOption
                : agentOption + " " + existingArgLine;
        // -Dblastradius.trackChild=true: this subprocess runs against the same pom that
        // binds this very goal, so without the flag its own SelectMojo execution would
        // resolve TRACK again (same commit, same baseRef) and recurse without bound — see
        // SelectMojo.trackChild's javadoc.
        ProcessBuilder processBuilder = new ProcessBuilder(
                "mvn", "-B", "--no-transfer-progress", "-Dblastradius.trackChild=true",
                "-DargLine=" + trackedArgLine, "clean", "test")
                .directory(projectDir.toFile())
                .redirectErrorStream(true);

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
            if (process.exitValue() != 0) {
                throw new IllegalStateException("track run failed against " + projectDir + " with exit code "
                        + process.exitValue() + "; refusing to persist a partial dependency index.\n"
                        + "Last output from the tracking build:\n" + readOutputTail(outputFile));
            }
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

    private static String readOutputTail(Path outputFile) {
        try (SeekableByteChannel channel = Files.newByteChannel(outputFile)) {
            long size = channel.size();
            int length = (int) Math.min(size, FAILURE_OUTPUT_TAIL_BYTES);
            channel.position(Math.max(0, size - length));
            ByteBuffer buffer = ByteBuffer.allocate(length);
            while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                // Continue until the requested tail is fully read or the file ends.
            }
            return StandardCharsets.UTF_8.decode(buffer.flip()).toString();
        } catch (IOException e) {
            return "(could not read tracking build output: " + e.getMessage() + ")";
        }
    }
}
