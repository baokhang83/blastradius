package io.github.baokhang83.blastradius.core.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persists {@link DependencyTrackingAgent#recordedDependencies()} (and
 * {@link DependencyTrackingAgent#ambientDependencies()}) to a file, so a parent process can
 * read it back after the agent's subprocess JVM exits (research.md #1).
 */
public final class DependencyRecordWriter {

    /**
     * Suffix for a marker file recorded next to a per-JVM output file when the shutdown
     * hook fails before it can write real data (see {@link DependencyTrackingAgent}). It
     * shares the output file's {@code <prefix>.<pid>} name so {@link DependencyRecordReader}
     * discovers it alongside its siblings, and uses that reason instead of the uninformative
     * "no files found" when a JVM's own crash is the only trace left behind.
     */
    static final String CRASH_MARKER_SUFFIX = ".crashed";

    private final ObjectMapper mapper = new ObjectMapper();

    public void write(Path outputFile, Map<TestIdentity, Map<String, String>> recordedDependencies) {
        write(outputFile, recordedDependencies, Set.of());
    }

    public void write(Path outputFile, Map<TestIdentity, Map<String, String>> recordedDependencies,
            Set<String> ambientDependencies) {
        List<DependencyRecord> records = recordedDependencies.entrySet().stream()
                .map(entry -> new DependencyRecord(entry.getKey(), entry.getValue()))
                .toList();
        try {
            mapper.writeValue(outputFile.toFile(), new DependencyRecordFile(records, ambientDependencies));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write dependency record to " + outputFile, e);
        }
    }

    /**
     * Records why this JVM's shutdown hook produced no dependency data, so a caller sees
     * a concrete crash reason instead of silence indistinguishable from "the agent was
     * never attached".
     */
    public void writeCrashMarker(Path outputFile, Throwable failure) {
        Path markerFile = Path.of(outputFile + CRASH_MARKER_SUFFIX);
        String reason = failure.getClass().getName() + ": " + failure.getMessage();
        try {
            Files.writeString(markerFile, reason);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write crash marker to " + markerFile, e);
        }
    }
}
