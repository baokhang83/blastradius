package io.github.baokhang83.blastradius.core.tracking;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Reads back what {@link DependencyRecordWriter} persisted. */
public final class DependencyRecordReader {

    private final ObjectMapper mapper = new ObjectMapper();

    public Map<TestIdentity, Map<String, String>> read(Path inputFile) {
        try {
            List<DependencyRecord> records =
                    mapper.readValue(inputFile.toFile(), new TypeReference<List<DependencyRecord>>() {});
            return records.stream()
                    .collect(Collectors.toUnmodifiableMap(DependencyRecord::test, DependencyRecord::dependencies));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read dependency record from " + inputFile, e);
        }
    }

    /**
     * Reads and merges every {@code <baseOutputFile>.<pid>} sibling file that
     * {@link DependencyTrackingAgent} wrote — one per JVM that had the agent attached
     * (see {@code DependencyTrackingAgent#premain}). A build with multiple forked test
     * JVMs (e.g. a target project configured with {@code reuseForks=false}) produces one
     * such file per fork; this merges them all into a single map, as if it had all been
     * recorded by one JVM.
     */
    public Map<TestIdentity, Map<String, String>> readAll(Path baseOutputFile) {
        Path parent = baseOutputFile.toAbsolutePath().getParent();
        String prefix = baseOutputFile.getFileName().toString() + ".";
        Map<TestIdentity, Map<String, String>> merged = new HashMap<>();
        boolean foundAny = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent, prefix + "*")) {
            for (Path siblingFile : stream) {
                foundAny = true;
                read(siblingFile).forEach((test, classes) -> merged.merge(test, classes, (oldClasses, newClasses) -> {
                    Map<String, String> combined = new HashMap<>(oldClasses);
                    combined.putAll(newClasses);
                    return combined;
                }));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list dependency record files matching " + prefix + "* in "
                    + parent, e);
        }
        if (!foundAny) {
            throw new UncheckedIOException(
                    "failed to read dependency record from " + baseOutputFile,
                    new IOException("no files matching " + prefix + "* found in " + parent));
        }
        return Map.copyOf(merged);
    }
}
