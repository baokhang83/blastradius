package io.github.baokhang83.blastradius.gradle;

import io.github.baokhang83.blastradius.core.index.CommitIndexKey;
import io.github.baokhang83.blastradius.core.index.IndexStore;
import io.github.baokhang83.blastradius.core.tracking.DependencyRecordReader;
import io.github.baokhang83.blastradius.core.tracking.DependencyRecordSet;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;

/** Merges the completed test-worker records into the shared TRACK index. */
final class WriteTrackingIndexAction implements Action<Task> {

    private final File repositoryDirectory;
    private final String indexPathKey;
    private final File recordPrefix;
    private final String anchorCommit;
    private final ConfiguredIndexStore configuredStore;

    WriteTrackingIndexAction(File repositoryDirectory, String indexPathKey, File recordPrefix, String anchorCommit, ConfiguredIndexStore configuredStore) {
        this.repositoryDirectory = repositoryDirectory;
        this.indexPathKey = indexPathKey;
        this.recordPrefix = recordPrefix;
        this.anchorCommit = anchorCommit;
        this.configuredStore = configuredStore;
    }

    @Override
    public void execute(Task task) {
        try {
            DependencyRecordSet recorded = new DependencyRecordReader().readAll(recordPrefix.toPath());
            List<DependencyIndex.TestDependencyEntry> entries = recorded.tests().entrySet().stream()
                    .map(entry -> new DependencyIndex.TestDependencyEntry(entry.getKey(), entry.getValue().keySet()))
                    .toList();
            List<DependencyIndex.TestDirectInvocationEntry> directInvocations = recorded.directInvocations().entrySet().stream()
                    .map(entry -> new DependencyIndex.TestDirectInvocationEntry(entry.getKey(), entry.getValue()))
                    .toList();
            Path repositoryRoot = repositoryDirectory.toPath().toAbsolutePath().normalize();
            String indexKey = CommitIndexKey.forCommit(indexPathKey, anchorCommit);
            IndexStore<DependencyIndex> store = configuredStore.create(repositoryDirectory);
            try {
                store.put(indexKey, new DependencyIndex(
                        anchorCommit, Instant.now().toString(), entries, directInvocations, recorded.ambientDependencies()));
            } finally {
                ConfiguredIndexStore.close(store);
            }
            task.getLogger().lifecycle("[blastradius] TRACK — {} / {} tests selected", entries.size(), entries.size());
        } catch (RuntimeException e) {
            throw new GradleException("failed to create the blastradius dependency index after TRACK", e);
        }
    }
}
