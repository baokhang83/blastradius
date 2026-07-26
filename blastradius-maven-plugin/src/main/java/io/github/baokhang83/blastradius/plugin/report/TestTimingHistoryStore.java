package io.github.baokhang83.blastradius.plugin.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads and writes the optional local timing cache. A bad cache never changes selection. */
public final class TestTimingHistoryStore {

    private final ObjectMapper mapper = new ObjectMapper();

    public TestTimingHistory load(Path file) {
        if (!Files.isRegularFile(file)) {
            return TestTimingHistory.empty();
        }
        try {
            return mapper.readValue(file.toFile(), TestTimingHistory.class);
        } catch (IOException | IllegalArgumentException e) {
            return TestTimingHistory.empty();
        }
    }

    public void save(Path file, TestTimingHistory history) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            mapper.writeValue(file.toFile(), history);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write timing history to " + file, e);
        }
    }
}
