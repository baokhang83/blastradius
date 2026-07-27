package io.github.baokhang83.blastradius.core.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
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
}
