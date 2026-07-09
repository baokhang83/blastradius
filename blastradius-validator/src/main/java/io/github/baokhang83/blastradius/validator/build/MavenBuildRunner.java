package io.github.baokhang83.blastradius.validator.build;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Invokes a target project's own {@code mvn test} as a subprocess — never reimplementing
 * or second-guessing the project's real build (research.md #2) — optionally attaching
 * the dependency-tracking agent via {@code -DargLine=-javaagent:...=<outputFile>}, which
 * Surefire and Failsafe both honor without any pom.xml changes to the target project.
 */
public final class MavenBuildRunner {

    private static final long TIMEOUT_MINUTES = 5;

    /**
     * @param projectDir       the (already checked-out) working copy to build
     * @param agentJar         path to this tool's own shaded jar, or {@code null} to run
     *                         without attaching the tracking agent
     * @param dependencyRecordOutputFile where the agent should write its recorded
     *                         dependencies, or {@code null} if {@code agentJar} is null
     */
    public BuildResult run(Path projectDir, Path agentJar, Path dependencyRecordOutputFile) {
        return execute(projectDir, command(null), agentJar, dependencyRecordOutputFile);
    }

    /**
     * Runs only the named test (via Surefire's {@code -Dtest=} selector), with no agent
     * attached — used to confirm a failure isn't flaky (FR-013) without re-deriving
     * dependencies, which the original full run already captured.
     */
    public BuildResult runSingleTest(Path projectDir, TestIdentity test) {
        String selector = test.methodName() == null
                ? test.className()
                : test.className() + "#" + test.methodName();
        return execute(projectDir, command(selector), null, null);
    }

    private BuildResult execute(Path projectDir, String[] command, Path agentJar, Path dependencyRecordOutputFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(projectDir.toFile())
                    .redirectErrorStream(true);
            if (agentJar != null) {
                // JAVA_TOOL_OPTIONS is read directly by every JVM launch (Maven's own
                // process AND any forked Surefire test JVMs it spawns), independent of
                // Maven's argLine property — unlike -DargLine, which real target
                // projects routinely defeat, either by hardcoding a literal <argLine>
                // (jsoup) or via a plugin like JaCoCo's prepare-agent goal overwriting
                // the argLine property after our own override was set (Apache Commons).
                // The parent Maven JVM also picks up the agent this way, but it never
                // runs any JUnit test, so DependencyTrackingAgent's shutdown hook sees
                // an empty record and skips writing rather than clobbering the real
                // data the forked test JVM already wrote.
                String agentOpt = "-javaagent:" + agentJar.toAbsolutePath() + "="
                        + dependencyRecordOutputFile.toAbsolutePath();
                pb.environment().merge("JAVA_TOOL_OPTIONS", agentOpt, (existing, added) -> existing + " " + added);
            }
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("mvn test timed out against " + projectDir);
            }
            return new BuildResult(process.exitValue(), output);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to invoke mvn test against " + projectDir, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for mvn test against " + projectDir, e);
        }
    }

    private static String[] command(String testSelector) {
        // `clean` is required, not cosmetic: CommitCheckout reuses one scratch working
        // copy across every commit in the window, and `target/` is untracked — a
        // previous commit's build artifacts (including surefire-reports) would
        // otherwise silently survive a `git checkout` to a different commit.
        List<String> args = new ArrayList<>(List.of("mvn", "-B", "--no-transfer-progress", "clean", "test"));
        if (testSelector != null) {
            args.add("-Dtest=" + testSelector);
        }
        return args.toArray(new String[0]);
    }
}
