package io.github.baokhang83.blastradius.gradle;

import io.github.baokhang83.blastradius.core.tracking.DependencyRecordReader;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.tasks.testing.Test;

/** Attaches the tracking agent to a base-reference {@link Test} task and writes its index. */
final class GradleTrackAction {

    private final Project project;
    private final BlastradiusExtension extension;
    private final CurrentChangesResolver currentChangesResolver = new CurrentChangesResolver();
    private final IndexPathResolver indexPathResolver = new IndexPathResolver();
    private final AgentJarLocator agentJarLocator = new AgentJarLocator();
    private final DependencyRecordReader recordReader = new DependencyRecordReader();
    private final DependencyIndexWriter indexWriter = new DependencyIndexWriter();
    private TrackingRun trackingRun;

    GradleTrackAction(Project project, BlastradiusExtension extension) {
        this.project = project;
        this.extension = extension;
    }

    boolean prepare(Test test) {
        Path projectRoot = project.getRootDir().toPath();
        CurrentChanges changes = currentChangesResolver.resolve(projectRoot, extension.getBaseRef().get());
        if (!changes.baseRefBuild()) {
            return false;
        }

        Path recordPrefix = createRecordPrefix(test);
        Path agentJar = agentJarLocator.locate();
        test.jvmArgs("-javaagent:" + agentJar.toAbsolutePath() + "=" + recordPrefix.toAbsolutePath());
        trackingRun = new TrackingRun(changes.currentCommit(), indexPathResolver.resolve(project, extension), recordPrefix);
        project.getLogger().info("[blastradius] TRACK — collecting test dependencies from {}", test.getPath());
        return true;
    }

    void complete() {
        if (trackingRun == null) {
            return;
        }
        try {
            Map<TestIdentity, Map<String, String>> recorded = recordReader.readAll(trackingRun.recordPrefix());
            List<DependencyIndex.TestDependencyEntry> entries = recorded.entrySet().stream()
                    .map(entry -> new DependencyIndex.TestDependencyEntry(entry.getKey(), entry.getValue().keySet()))
                    .toList();
            indexWriter.write(
                    trackingRun.indexPath(), new DependencyIndex(trackingRun.anchorCommit(), Instant.now().toString(), entries));
            project.getLogger().lifecycle(
                    "[blastradius] TRACK — {} / {} tests selected", entries.size(), entries.size());
        } catch (RuntimeException e) {
            throw new GradleException("failed to create the blastradius dependency index after TRACK", e);
        }
    }

    private static Path createRecordPrefix(Test test) {
        try {
            Path temporaryDirectory = test.getTemporaryDir().toPath();
            Files.createDirectories(temporaryDirectory);
            Path prefix = Files.createTempFile(temporaryDirectory, "blastradius-dependencies-", ".json");
            Files.deleteIfExists(prefix);
            return prefix;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create a temporary dependency-record path for " + test.getPath(), e);
        }
    }

    private record TrackingRun(String anchorCommit, Path indexPath, Path recordPrefix) {}
}
