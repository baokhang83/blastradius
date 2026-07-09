package io.github.baokhang83.blastradius.core.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Persists {@link DependencyTrackingAgent#recordedDependencies()} to a file, so a parent
 * process can read it back after the agent's subprocess JVM exits (research.md #1).
 */
public final class DependencyRecordWriter {

    private final ObjectMapper mapper = new ObjectMapper();

    public void write(Path outputFile, Map<TestIdentity, Map<String, String>> recordedDependencies) {
        List<DependencyRecord> records = recordedDependencies.entrySet().stream()
                .map(entry -> new DependencyRecord(entry.getKey(), entry.getValue()))
                .toList();
        try {
            mapper.writeValue(outputFile.toFile(), records);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write dependency record to " + outputFile, e);
        }
    }
}
