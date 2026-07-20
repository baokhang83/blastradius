package io.github.baokhang83.blastradius.gradle;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

final class DependencyIndexReader {

    private final ObjectMapper mapper = new ObjectMapper();

    DependencyIndex read(Path inputFile) {
        try {
            return mapper.readValue(inputFile.toFile(), DependencyIndex.class);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read dependency index from " + inputFile, e);
        }
    }
}
