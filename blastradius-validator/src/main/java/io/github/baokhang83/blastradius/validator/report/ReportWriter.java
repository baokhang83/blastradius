package io.github.baokhang83.blastradius.validator.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** Writes the {@link AnalysisReport} to disk as indented JSON (FR-010). */
public final class ReportWriter {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void write(Path outputFile, AnalysisReport report) {
        try {
            mapper.writeValue(outputFile.toFile(), report);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write report to " + outputFile, e);
        }
    }
}
