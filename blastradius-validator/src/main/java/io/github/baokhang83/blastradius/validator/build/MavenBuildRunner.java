package io.github.baokhang83.blastradius.validator.build;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Invokes a target project's own {@code mvn test} as a subprocess — never reimplementing
 * or second-guessing the project's real build (research.md #2) — optionally attaching
 * the dependency-tracking agent via {@code -DargLine=-javaagent:...=<outputFile>}, which
 * Surefire and Failsafe both honor without any pom.xml changes to the target project.
 */
public final class MavenBuildRunner {

    private static final long TIMEOUT_MINUTES = 5;

    private final Integer parallelThreads;

    /** No {@code -T} flag: the target project's reactor builds serially, as it always has. */
    public MavenBuildRunner() {
        this(null);
    }

    /**
     * @param parallelThreads value passed to Maven's own {@code -T} reactor-parallelism
     *                        flag (e.g. {@code 4}), or {@code null} to omit it and build
     *                        serially. Each module still builds in dependency order and
     *                        the tracking agent attaches identically per fork; the risk
     *                        this trades in is target projects with non-thread-safe
     *                        build plugins that only misbehave under a parallel reactor.
     */
    public MavenBuildRunner(Integer parallelThreads) {
        if (parallelThreads != null && parallelThreads < 1) {
            throw new IllegalArgumentException("parallelThreads must be positive, got: " + parallelThreads);
        }
        this.parallelThreads = parallelThreads;
    }

    /**
     * How long to wait for a Surefire-forked JVM that outlives {@code mvn} itself to exit
     * on its own before it gets forcibly killed (research.md #1 / apache/shenyu finding,
     * see {@link #reapStragglers}).
     */
    private static final Duration DESCENDANT_GRACE_PERIOD = Duration.ofMinutes(5);

    private static final Duration DESCENDANT_KILL_GRACE_PERIOD = Duration.ofSeconds(30);
    private static final Duration DESCENDANT_POLL_INTERVAL = Duration.ofMillis(200);

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
     * dependencies, which the original full run already captured. Unscoped: walks the
     * whole reactor. Prefer {@link #runSingleTest(Path, TestIdentity, Path)} when the
     * test's module is known.
     */
    public BuildResult runSingleTest(Path projectDir, TestIdentity test) {
        return runSingleTest(projectDir, test, null);
    }

    /**
     * Like {@link #runSingleTest(Path, TestIdentity)}, but scopes the rebuild to
     * {@code moduleDir} (plus its upstream dependencies via {@code -am}) instead of the
     * whole reactor, via Maven's {@code -pl}.
     *
     * @param moduleDir the reactor module that actually contains {@code test}, or
     *                   {@code null} to build unscoped (e.g. a single-module target
     *                   project, or when the caller hasn't resolved which module the test
     *                   lives in)
     */
    public BuildResult runSingleTest(Path projectDir, TestIdentity test, Path moduleDir) {
        String selector = test.methodName() == null
                ? test.className()
                : test.className() + "#" + test.methodName();
        return execute(projectDir, command(selector, relativeModulePath(projectDir, moduleDir)), null, null);
    }

    /**
     * @return {@code moduleDir}'s path relative to {@code projectDir} for use as an
     *         {@code -pl} argument, or {@code null} if {@code moduleDir} is {@code null}
     *         or equals {@code projectDir} itself (a single-module target project, where
     *         {@code -pl} would be redundant).
     */
    private static String relativeModulePath(Path projectDir, Path moduleDir) {
        if (moduleDir == null) {
            return null;
        }
        String relative = projectDir.toAbsolutePath().normalize()
                .relativize(moduleDir.toAbsolutePath().normalize())
                .toString();
        return relative.isEmpty() ? null : relative;
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
            // Read stdout on a separate thread: the descendant-tracking poll below must
            // run concurrently, not after, or a chatty child can fill the OS pipe buffer
            // and block forever with nobody draining it.
            byte[][] outputHolder = new byte[1][];
            Thread outputReader = new Thread(() -> {
                try {
                    outputHolder[0] = process.getInputStream().readAllBytes();
                } catch (IOException e) {
                    outputHolder[0] = new byte[0];
                }
            });
            outputReader.start();

            Set<ProcessHandle> descendants =
                    awaitDescendantsWhileAlive(process, Duration.ofMinutes(TIMEOUT_MINUTES));
            outputReader.join();

            if (process.isAlive()) {
                process.destroyForcibly();
                descendants.forEach(ProcessHandle::destroyForcibly);
                throw new IllegalStateException("mvn test timed out against " + projectDir);
            }
            // mvn itself exiting is not proof every process it spawned has: Surefire can
            // give up on and report a broken/unresponsive fork as failed the instant its
            // communication pipe breaks, without waiting for that fork's own OS process
            // to exit (found running against apache/shenyu). An orphaned fork left alive
            // past this point hasn't run DependencyTrackingAgent's shutdown hook yet, so
            // reading its output immediately would race it and find nothing at all.
            reapStragglers(descendants, DESCENDANT_GRACE_PERIOD);

            String output = new String(outputHolder[0], StandardCharsets.UTF_8);
            return new BuildResult(process.exitValue(), output);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to invoke mvn test against " + projectDir, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for mvn test against " + projectDir, e);
        }
    }

    /**
     * Polls {@code process}'s descendants while it's alive, accumulating every one ever
     * observed, until it exits or {@code timeout} elapses. Descendants must be sampled
     * <em>while</em> the parent is alive: once it exits, the OS reparents any surviving
     * children away, and they silently drop out of {@link Process#toHandle()}'s
     * {@code descendants()} from that point on — checking only after {@code process} has
     * already exited can miss a straggler entirely.
     */
    static Set<ProcessHandle> awaitDescendantsWhileAlive(Process process, Duration timeout) throws InterruptedException {
        Set<ProcessHandle> descendants = new HashSet<>();
        Instant deadline = Instant.now().plus(timeout);
        while (process.isAlive() && Instant.now().isBefore(deadline)) {
            process.toHandle().descendants().forEach(descendants::add);
            Thread.sleep(DESCENDANT_POLL_INTERVAL.toMillis());
        }
        process.toHandle().descendants().forEach(descendants::add);
        return descendants;
    }

    /**
     * Gives every still-alive descendant up to {@code gracePeriod} to exit on its own,
     * then sends a graceful {@code destroy()} (which a JVM honors as a shutdown-hook
     * trigger, giving {@code DependencyTrackingAgent} a last chance to write a crash
     * marker) and, if it still hasn't exited after a short additional grace period,
     * {@code destroyForcibly()} so no orphaned Surefire fork is left running indefinitely.
     */
    static void reapStragglers(Set<ProcessHandle> descendants, Duration gracePeriod) throws InterruptedException {
        Instant deadline = Instant.now().plus(gracePeriod);
        while (descendants.stream().anyMatch(ProcessHandle::isAlive) && Instant.now().isBefore(deadline)) {
            Thread.sleep(DESCENDANT_POLL_INTERVAL.toMillis());
        }
        descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroy);

        Instant killDeadline = Instant.now().plus(DESCENDANT_KILL_GRACE_PERIOD);
        while (descendants.stream().anyMatch(ProcessHandle::isAlive) && Instant.now().isBefore(killDeadline)) {
            Thread.sleep(DESCENDANT_POLL_INTERVAL.toMillis());
        }
        descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    }

    String[] command(String testSelector) {
        return command(testSelector, null);
    }

    String[] command(String testSelector, String modulePath) {
        // `clean` is required, not cosmetic: CommitCheckout reuses one scratch working
        // copy across every commit in the window, and `target/` is untracked — a
        // previous commit's build artifacts (including surefire-reports) would
        // otherwise silently survive a `git checkout` to a different commit. With
        // modulePath set, -pl scopes `clean` (and everything else below) to just the
        // modules that are actually about to be rebuilt.
        List<String> args = new ArrayList<>(List.of("mvn", "-B", "--no-transfer-progress", "clean", "test"));
        if (parallelThreads != null) {
            args.add("-T");
            args.add(String.valueOf(parallelThreads));
        }
        if (testSelector != null) {
            args.add("-Dtest=" + testSelector);
            // Without this, a multi-module reactor aborts the whole build at the first
            // module that has test sources but none matching testSelector (found running
            // against apache/shenyu, whose shenyu-common module has no test named after
            // any admin-module class) — the named test's own module is never reached, so
            // confirmFailure (FR-013) always reads back no report and misclassifies a
            // genuinely flaky test as CONFIRMED_FAILED. Two properties because Surefire
            // has used both names for this switch across versions.
            args.add("-DfailIfNoTests=false");
            args.add("-Dsurefire.failIfNoSpecifiedTests=false");
        }
        if (modulePath != null) {
            // Scopes the rebuild to just the module that actually contains testSelector
            // (plus its upstream dependencies via -am) instead of walking every module in
            // the reactor. Without this, confirming one flaky test in a large multi-module
            // project means a full serial rebuild of every module per candidate — for
            // --fast-ground-truth's N+1 confirmation reruns against apache/shenyu's
            // ~230-candidate mapper suite, that turned a ~30 minute validator run into
            // many hours once the failIfNoTests fix above stopped the (wrong but fast)
            // early abort.
            args.add("-pl");
            args.add(modulePath);
            args.add("-am");
        }
        return args.toArray(new String[0]);
    }
}
