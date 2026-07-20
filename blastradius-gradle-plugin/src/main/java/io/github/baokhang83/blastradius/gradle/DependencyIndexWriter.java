package io.github.baokhang83.blastradius.gradle;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persists the shared dependency index produced by a Gradle TRACK build. */
final class DependencyIndexWriter {

    private final ObjectMapper mapper = new ObjectMapper();

    void write(Path outputFile, DependencyIndex index) {
        try {
            if (outputFile.getParent() != null) {
                Files.createDirectories(outputFile.getParent());
            }
            mapper.writeValue(outputFile.toFile(), index);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write dependency index to " + outputFile, e);
        }
    }
}
