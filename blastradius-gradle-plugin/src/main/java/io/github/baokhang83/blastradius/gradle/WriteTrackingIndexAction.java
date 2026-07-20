package io.github.baokhang83.blastradius.gradle;

import io.github.baokhang83.blastradius.core.tracking.DependencyRecordReader;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;

/** Merges the completed test-worker records into the shared TRACK index. */
final class WriteTrackingIndexAction implements Action<Task> {

    private final File indexFile;
    private final File recordPrefix;
    private final String anchorCommit;

    WriteTrackingIndexAction(File indexFile, File recordPrefix, String anchorCommit) {
        this.indexFile = indexFile;
        this.recordPrefix = recordPrefix;
        this.anchorCommit = anchorCommit;
    }

    @Override
    public void execute(Task task) {
        try {
            Map<TestIdentity, Map<String, String>> recorded = new DependencyRecordReader().readAll(recordPrefix.toPath());
            List<DependencyIndex.TestDependencyEntry> entries = recorded.entrySet().stream()
                    .map(entry -> new DependencyIndex.TestDependencyEntry(entry.getKey(), entry.getValue().keySet()))
                    .toList();
            new DependencyIndexWriter().write(
                    indexFile.toPath(), new DependencyIndex(anchorCommit, Instant.now().toString(), entries));
            task.getLogger().lifecycle("[blastradius] TRACK — {} / {} tests selected", entries.size(), entries.size());
        } catch (RuntimeException e) {
            throw new GradleException("failed to create the blastradius dependency index after TRACK", e);
        }
    }
}
