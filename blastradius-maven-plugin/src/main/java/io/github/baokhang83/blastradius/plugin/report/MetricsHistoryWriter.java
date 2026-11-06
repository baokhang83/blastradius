package io.github.baokhang83.blastradius.plugin.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Appends one independently readable metrics sample for each selection report. */
public final class MetricsHistoryWriter {
    private static final Object APPEND_LOCK = new Object();
    private final ObjectMapper mapper = new ObjectMapper();

    public void append(Path output, BuildReport report) {
        var sample = new Sample(1, Instant.now().toString(), report.mode().name(), report.selectedCount(),
                report.skippedCount(), report.totalCount(), report.estimatedTimeSavedMillis(),
                report.timingCoverage(), null);
        try {
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            byte[] bytes = (mapper.writeValueAsString(sample) + "\n").getBytes(StandardCharsets.UTF_8);
            synchronized (APPEND_LOCK) {
                try (FileChannel channel = FileChannel.open(output, StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                     FileLock lock = channel.lock()) {
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to append build metrics to " + output, e);
        }
    }

    /** Null audit misses means no audit result was available for this build. */
    record Sample(int schemaVersion, String recordedAt, String mode, int selectedCount, int skippedCount,
            int totalCount, Long estimatedTimeSavedMillis, TimingCoverage timingCoverage, Integer auditMisses) { }
}
