package io.github.baokhang83.blastradius.plugin.mojo;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.project.MavenProject;

/**
 * Narrows the project's effective Surefire/Failsafe test selection to a specific set of
 * tests — Surefire's own {@code test} parameter is already bound to the {@code test}
 * Maven property by convention (the same property {@code -Dtest=} sets on the command
 * line), so setting it here, before the {@code test} phase runs, has the same effect
 * without needing a command-line override (research.md #1: a plain test-selection
 * filter, unrelated to the {@code argLine}/javaagent mechanism).
 */
public final class SurefireFilterApplier {

    public void apply(MavenProject project, Set<TestIdentity> selectedTests) {
        String filter = selectedTests.stream()
                .map(SurefireFilterApplier::toSurefirePattern)
                .collect(Collectors.joining(","));
        project.getProperties().setProperty("test", filter);
    }

    private static String toSurefirePattern(TestIdentity test) {
        return test.methodName() == null ? test.className() : test.className() + "#" + test.methodName();
    }
}
