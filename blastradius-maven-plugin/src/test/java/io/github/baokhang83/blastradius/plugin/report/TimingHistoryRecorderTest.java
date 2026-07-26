package io.github.baokhang83.blastradius.plugin.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TimingHistoryRecorderTest {

    @Test
    void capturesSurefireResultsAfterItsMojoCompletes(@TempDir Path tempDir) throws Exception {
        MavenProject project = new MavenProject();
        project.setFile(tempDir.resolve("pom.xml").toFile());
        project.getBuild().setDirectory(tempDir.resolve("target").toString());
        Path reports = Files.createDirectories(tempDir.resolve("target/surefire-reports"));
        Files.writeString(reports.resolve("TEST-example.FooTest.xml"), """
                <testsuite name="example.FooTest">
                  <testcase name="checksFoo" classname="example.FooTest" time="0.125"/>
                </testsuite>
                """);

        Path historyFile = tempDir.resolve(".blastradius/test-timings.json");
        TestTimingHistoryStore store = new TestTimingHistoryStore();
        TimingHistoryRecorder recorder = new TimingHistoryRecorder(
                new org.apache.maven.execution.AbstractExecutionListener(), store, new SurefireReportReader(), ignored -> { });
        recorder.mojoSucceeded(eventFor(project, "maven-surefire-plugin"));

        assertEquals(125L, store.load(historyFile).durationsByTest()
                .get(new TestIdentity("example.FooTest", "checksFoo")));
    }

    private static ExecutionEvent eventFor(MavenProject project, String artifactId) {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId(artifactId);
        MojoExecution execution = new MojoExecution(plugin, "test", "default-test");
        return new ExecutionEvent() {
            @Override public Type getType() { return Type.MojoSucceeded; }
            @Override public MavenSession getSession() { return null; }
            @Override public MavenProject getProject() { return project; }
            @Override public MojoExecution getMojoExecution() { return execution; }
            @Override public Exception getException() { return null; }
        };
    }
}
