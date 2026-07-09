package io.github.baokhang83.blastradius.plugin.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persists a {@link DependencyIndex} to a file. */
public final class DependencyIndexWriter {

    private final ObjectMapper mapper = new ObjectMapper();

    public void write(Path outputFile, DependencyIndex index) {
        try {
            // .blastradius/ won't exist yet on a project's first-ever TRACK run.
            if (outputFile.getParent() != null) {
                Files.createDirectories(outputFile.getParent());
            }
            mapper.writeValue(outputFile.toFile(), index);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write dependency index to " + outputFile, e);
        }
    }
}
