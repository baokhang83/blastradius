package io.github.baokhang83.blastradius.plugin.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** Reads back what {@link DependencyIndexWriter} persisted. */
public final class DependencyIndexReader {

    private final ObjectMapper mapper = new ObjectMapper();

    public DependencyIndex read(Path inputFile) {
        try {
            return mapper.readValue(inputFile.toFile(), DependencyIndex.class);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read dependency index from " + inputFile, e);
        }
    }
}
