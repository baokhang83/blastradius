package io.github.baokhang83.blastradius.plugin.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persists a {@link BuildReport} to a file. */
public final class BuildReportWriter {

    private final ObjectMapper mapper = new ObjectMapper();

    public void write(Path outputFile, BuildReport report) {
        try {
            if (outputFile.getParent() != null) {
                Files.createDirectories(outputFile.getParent());
            }
            mapper.writeValue(outputFile.toFile(), report);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write build report to " + outputFile, e);
        }
    }
}
