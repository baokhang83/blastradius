package io.github.baokhang83.blastradius.core.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Reads back what {@link DependencyRecordWriter} persisted. */
public final class DependencyRecordReader {

    private final ObjectMapper mapper = new ObjectMapper();

    public Map<TestIdentity, Map<String, String>> read(Path inputFile) {
        return readFile(inputFile).tests();
    }

    private DependencyRecordSet readFile(Path inputFile) {
        try {
            DependencyRecordFile file = mapper.readValue(inputFile.toFile(), DependencyRecordFile.class);
            Map<TestIdentity, Map<String, String>> tests = file.tests().stream()
                    .collect(Collectors.toUnmodifiableMap(DependencyRecord::test, DependencyRecord::dependencies));
            return new DependencyRecordSet(tests, file.ambientDependencies());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read dependency record from " + inputFile, e);
        }
    }

    /**
     * Reads and merges every {@code <baseOutputFile>.<pid>} sibling file that
     * {@link DependencyTrackingAgent} wrote — one per JVM that had the agent attached
     * (see {@code DependencyTrackingAgent#premain}). A build with multiple forked test
     * JVMs (e.g. a target project configured with {@code reuseForks=false}) produces one
     * such file per fork; this merges them all into a single {@link DependencyRecordSet},
     * as if it had all been recorded by one JVM — including the {@code ambientDependencies},
     * since each fork's discovery pass can force-load a different subset of classes.
     *
     * <p>A test class itself is excluded from the merged ambient set: JUnit Platform's
     * discovery pass loads every discovered test class (to introspect for {@code @Test}
     * methods) before any test's tracking window opens, in every fork, regardless of what
     * that test actually depends on — so its own class name is unconditionally ambient and
     * carries no dependency-graph signal, unlike a production class force-loaded before
     * tracking could attribute it. Left unfiltered, changing any single test file would
     * trigger the ambient fallback for the entire reactor, every time.
     */
    public DependencyRecordSet readAll(Path baseOutputFile) {
        Path parent = baseOutputFile.toAbsolutePath().getParent();
        String prefix = baseOutputFile.getFileName().toString() + ".";
        Map<TestIdentity, Map<String, String>> mergedTests = new HashMap<>();
        Set<String> mergedAmbient = new HashSet<>();
        List<String> crashReasons = new ArrayList<>();
        boolean foundAny = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent, prefix + "*")) {
            for (Path siblingFile : stream) {
                if (siblingFile.getFileName().toString().endsWith(DependencyRecordWriter.CRASH_MARKER_SUFFIX)) {
                    crashReasons.add(readCrashReason(siblingFile));
                    continue;
                }
                foundAny = true;
                DependencyRecordSet sibling = readFile(siblingFile);
                sibling.tests().forEach((test, classes) -> mergedTests.merge(test, classes, (oldClasses, newClasses) -> {
                    Map<String, String> combined = new HashMap<>(oldClasses);
                    combined.putAll(newClasses);
                    return combined;
                }));
                mergedAmbient.addAll(sibling.ambientDependencies());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list dependency record files matching " + prefix + "* in "
                    + parent, e);
        }
        if (!foundAny) {
            String reason = crashReasons.isEmpty()
                    ? "no files matching " + prefix + "* found in " + parent
                    : "the tracking agent's shutdown hook crashed before writing any data: "
                            + String.join("; ", crashReasons);
            throw new UncheckedIOException(
                    "failed to read dependency record from " + baseOutputFile, new IOException(reason));
        }
        for (TestIdentity test : mergedTests.keySet()) {
            mergedAmbient.remove(test.className());
        }
        return new DependencyRecordSet(Map.copyOf(mergedTests), Set.copyOf(mergedAmbient));
    }

    private static String readCrashReason(Path markerFile) {
        try {
            return Files.readString(markerFile);
        } catch (IOException e) {
            return "crash marker at " + markerFile + " could not be read: " + e.getMessage();
        }
    }
}
