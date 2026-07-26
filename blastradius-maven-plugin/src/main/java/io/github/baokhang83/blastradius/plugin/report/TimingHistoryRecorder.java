package io.github.baokhang83.blastradius.plugin.report;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;

/**
 * Chains Maven's session listener and stores standard test-report timings when Surefire or
 * Failsafe completes. Recording is observational: a timing-cache problem cannot fail a build.
 */
public final class TimingHistoryRecorder implements ExecutionListener {

    private final ExecutionListener delegate;
    private final TestTimingHistoryStore store;
    private final SurefireReportReader reader;
    private final Consumer<String> warning;

    public TimingHistoryRecorder(ExecutionListener delegate, TestTimingHistoryStore store,
            SurefireReportReader reader, Consumer<String> warning) {
        this.delegate = delegate;
        this.store = store;
        this.reader = reader;
        this.warning = warning;
    }

    @Override public void projectDiscoveryStarted(ExecutionEvent event) { delegate.projectDiscoveryStarted(event); }
    @Override public void sessionStarted(ExecutionEvent event) { delegate.sessionStarted(event); }
    @Override public void sessionEnded(ExecutionEvent event) { delegate.sessionEnded(event); }
    @Override public void projectSkipped(ExecutionEvent event) { delegate.projectSkipped(event); }
    @Override public void projectStarted(ExecutionEvent event) { delegate.projectStarted(event); }
    @Override public void projectSucceeded(ExecutionEvent event) { delegate.projectSucceeded(event); }
    @Override public void projectFailed(ExecutionEvent event) { delegate.projectFailed(event); }
    @Override public void mojoSkipped(ExecutionEvent event) { delegate.mojoSkipped(event); }
    @Override public void mojoStarted(ExecutionEvent event) { delegate.mojoStarted(event); }

    @Override
    public void mojoSucceeded(ExecutionEvent event) {
        delegate.mojoSucceeded(event);
        record(event);
    }

    @Override
    public void mojoFailed(ExecutionEvent event) {
        delegate.mojoFailed(event);
        record(event);
    }

    @Override public void forkStarted(ExecutionEvent event) { delegate.forkStarted(event); }
    @Override public void forkSucceeded(ExecutionEvent event) { delegate.forkSucceeded(event); }
    @Override public void forkFailed(ExecutionEvent event) { delegate.forkFailed(event); }
    @Override public void forkedProjectStarted(ExecutionEvent event) { delegate.forkedProjectStarted(event); }
    @Override public void forkedProjectSucceeded(ExecutionEvent event) { delegate.forkedProjectSucceeded(event); }
    @Override public void forkedProjectFailed(ExecutionEvent event) { delegate.forkedProjectFailed(event); }

    private synchronized void record(ExecutionEvent event) {
        if (!isTestEngine(event)) {
            return;
        }
        try {
            Path buildDir = Path.of(event.getProject().getBuild().getDirectory());
            String reportsDirectory = "maven-failsafe-plugin".equals(event.getMojoExecution().getArtifactId())
                    ? "failsafe-reports" : "surefire-reports";
            Map<TestIdentity, Long> observed = reader.read(buildDir.resolve(reportsDirectory));
            if (observed.isEmpty()) {
                return;
            }
            Path historyFile = event.getProject().getBasedir().toPath().resolve(".blastradius/test-timings.json");
            Map<TestIdentity, Long> merged = new LinkedHashMap<>(store.load(historyFile).durationsByTest());
            merged.putAll(observed);
            store.save(historyFile, TestTimingHistory.from(merged));
        } catch (RuntimeException e) {
            warning.accept("[blastradius] could not record test timings: " + e.getMessage());
        }
    }

    private static boolean isTestEngine(ExecutionEvent event) {
        String artifactId = event.getMojoExecution().getArtifactId();
        return "maven-surefire-plugin".equals(artifactId) || "maven-failsafe-plugin".equals(artifactId);
    }
}
