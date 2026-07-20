package io.github.baokhang83.blastradius.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import org.gradle.api.Action;
import org.gradle.api.Task;

/** Clears prior worker records before a TRACK-mode {@code Test} task starts its JVMs. */
final class PrepareTrackingAction implements Action<Task> {

    private final File recordPrefix;

    PrepareTrackingAction(File recordPrefix) {
        this.recordPrefix = recordPrefix;
    }

    @Override
    public void execute(Task task) {
        try {
            Files.createDirectories(recordPrefix.toPath().getParent());
            try (DirectoryStream<java.nio.file.Path> records = Files.newDirectoryStream(
                    recordPrefix.toPath().getParent(), recordPrefix.getName() + ".*")) {
                for (java.nio.file.Path record : records) {
                    Files.deleteIfExists(record);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to prepare dependency records for " + task.getPath(), e);
        }
    }
}
