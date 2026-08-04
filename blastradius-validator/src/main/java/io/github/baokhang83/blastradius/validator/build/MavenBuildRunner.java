package io.github.baokhang83.blastradius.validator.build;

import io.github.baokhang83.blastradius.core.process.MavenLauncher;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.validator.git.CommitCheckout;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Invokes a target project's own {@code mvn test} as a subprocess — never reimplementing
 * or second-guessing the project's real build (research.md #2) — optionally attaching
 * the dependency-tracking agent via {@code -DargLine=-javaagent:...=<outputFile>}, which
 * Surefire and Failsafe both honor without any pom.xml changes to the target project.
 */
public final class MavenBuildRunner {

    /**
     * Default ceiling for a single {@code mvn} build, used when the operator doesn't override it
     * via {@code --build-timeout-minutes}. Five minutes fits a small-to-medium reactor; a large
     * one (e.g. apache/shenyu, whose full {@code clean test} runs well past this) needs a higher
     * value or every build is killed mid-flight and misreported as a build failure.
     */
    private static final long DEFAULT_TIMEOUT_MINUTES = 5;

    /**
     * Properties that switch off build-quality plugins which run during {@code clean test} but
     * cannot change <em>which tests run or whether they pass</em> — coverage instrumentation,
     * lint, license-header and resource-bundling checks. Appended only when the operator opts in
     * via {@code --skip-build-extras}. Kept to standard plugin skip properties (plus apache/shenyu's
     * own {@code skipRemoteResources}); on a project without a given plugin the matching property is
     * simply an unread system property, so the set is a harmless no-op rather than an error.
     *
     * <p>Deliberately does <em>not</em> include anything that alters test selection or execution
     * ({@code -DskipTests}, {@code -pl}, fork settings): those would silently corrupt the ground
     * truth this tool exists to establish (constitution §III), which is why this is a curated set
     * rather than a free-form {@code mvn} argument pass-through.
     */
    private static final List<String> BUILD_EXTRA_SKIPS = List.of(
            "-Djacoco.skip=true",
            "-Dcheckstyle.skip=true",
            "-Drat.skip=true",
            "-DskipRemoteResources=true");

    private final Integer parallelThreads;
    private final long timeoutMinutes;
    private final boolean isolatedRepo;
    private final boolean skipBuildExtras;
    private final SkippedTests skippedTests;

    /** No {@code -T} flag: the target project's reactor builds serially, as it always has. */
    public MavenBuildRunner() {
        this(null);
    }

    public MavenBuildRunner(Integer parallelThreads) {
        this(parallelThreads, DEFAULT_TIMEOUT_MINUTES);
    }

    public MavenBuildRunner(Integer parallelThreads, long timeoutMinutes) {
        this(parallelThreads, timeoutMinutes, false);
    }

    public MavenBuildRunner(Integer parallelThreads, long timeoutMinutes, boolean isolatedRepo) {
        this(parallelThreads, timeoutMinutes, isolatedRepo, false);
    }

    /**
     * @param parallelThreads value passed to Maven's own {@code -T} reactor-parallelism
     *                        flag (e.g. {@code 4}), or {@code null} to omit it and build
     *                        serially. Each module still builds in dependency order and
     *                        the tracking agent attaches identically per fork; the risk
     *                        this trades in is target projects with non-thread-safe
     *                        build plugins that only misbehave under a parallel reactor.
     * @param timeoutMinutes  how long a single {@code mvn} build may run before it is killed and
     *                        reported as timed out. Must be positive. A large reactor legitimately
     *                        exceeds the {@value #DEFAULT_TIMEOUT_MINUTES}-minute default, so this
     *                        is operator-tunable rather than fixed.
     * @param isolatedRepo    when {@code true}, each build runs against a private
     *                        {@code -Dmaven.repo.local} derived from its working copy
     *                        ({@link CommitCheckout#isolatedMavenRepoFor}) instead of the shared
     *                        {@code ~/.m2}. Set only for concurrent runs (build-concurrency &gt; 1):
     *                        it removes the local-repo <em>file-lock</em> contention that plugins
     *                        like {@code maven-remote-resources-plugin} suffer under parallel
     *                        builds (apache/shenyu's documented "Could not acquire lock(s)"), at
     *                        the cost of a one-time dependency download per distinct working copy.
     * @param skipBuildExtras when {@code true}, appends {@link #BUILD_EXTRA_SKIPS} to every build so
     *                        coverage/lint/license/resource-bundling plugins don't run. These change
     *                        neither which tests run nor whether they pass, so the ground truth is
     *                        unaffected (constitution §III); they are pure per-build wall-clock
     *                        overhead the operator can shed on projects that don't need them.
     */
    public MavenBuildRunner(Integer parallelThreads, long timeoutMinutes, boolean isolatedRepo,
            boolean skipBuildExtras) {
        this(parallelThreads, timeoutMinutes, isolatedRepo, skipBuildExtras, SkippedTests.none());
    }

    /** Creates a runner that consistently excludes the supplied known-flaky test classes. */
    public MavenBuildRunner(Integer parallelThreads, long timeoutMinutes, boolean isolatedRepo,
            boolean skipBuildExtras, SkippedTests skippedTests) {
        if (parallelThreads != null && parallelThreads < 1) {
            throw new IllegalArgumentException("parallelThreads must be positive, got: " + parallelThreads);
        }
        if (timeoutMinutes < 1) {
            throw new IllegalArgumentException("timeoutMinutes must be positive, got: " + timeoutMinutes);
        }
        this.parallelThreads = parallelThreads;
        this.timeoutMinutes = timeoutMinutes;
        this.isolatedRepo = isolatedRepo;
        this.skipBuildExtras = skipBuildExtras;
        this.skippedTests = java.util.Objects.requireNonNull(skippedTests, "skippedTests");
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
        return run(projectDir, agentJar, dependencyRecordOutputFile, null);
    }

    /**
     * Like {@link #run(Path, Path, Path)}, but scopes the full-suite build to {@code modulePath}
     * (plus its upstream dependencies via {@code -am} <em>and</em> its downstream dependents via
     * {@code -amd}) instead of the whole reactor. Used by mutation validation: a synthetic mutant
     * changes one file in one module, so the only tests whose outcome can change live in that
     * module or in a module that depends on it. {@code clean} is kept ({@code true}) because each
     * mutant runs on a freshly re-checked-out tree whose {@code target/} dirs were already wiped;
     * {@code -amd} is what keeps a legitimately-selected <em>downstream</em> killing test in scope,
     * so scoping never fabricates a would-miss (§III).
     *
     * @param modulePath the mutated module's repo-relative directory, or {@code null} to build the
     *                   whole reactor (e.g. the change maps to no single module)
     */
    public BuildResult run(Path projectDir, Path agentJar, Path dependencyRecordOutputFile, String modulePath) {
        return execute(projectDir, command(null, modulePath, true, true), agentJar, dependencyRecordOutputFile);
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
        return runTests(projectDir, List.of(test), moduleDir);
    }

    /**
     * Confirms a whole batch of failures in a <em>single</em> {@code mvn} invocation, by naming
     * every test in one comma-separated {@code -Dtest=} selector. Surefire reads that as "run each
     * of these", and each named test still executes in the same forked-JVM isolation it would get
     * if it were named alone — so the per-test verdict is identical to N separate reruns, while the
     * process count drops from N to 1.
     *
     * <p>That difference is the whole point. Mutation validation confirms every test a mutant broke,
     * and a mutant in a low-level class breaks a large slice of the suite (244 tests observed on a
     * single jsoup {@code StringUtil} mutant, ~27 on average). One {@code mvn} per failure made a
     * mutant cost tens of minutes and a pair cost hours; one {@code mvn} per <em>mutant</em> makes
     * that a single rerun regardless of how wide the blast radius was.
     *
     * @param tests the failures to confirm — must be non-empty, since an absent {@code -Dtest=}
     *              selector means "run the entire suite", the opposite of what a rerun wants
     * @param moduleDir the reactor module containing {@code tests}, or {@code null} to run unscoped;
     *                  callers group by module so one batch never spans two {@code -pl} scopes
     */
    public BuildResult runTests(Path projectDir, List<TestIdentity> tests, Path moduleDir) {
        if (tests.isEmpty()) {
            throw new IllegalArgumentException(
                    "refusing to rerun an empty batch: with no -Dtest= selector Maven runs the whole suite");
        }
        String selector = tests.stream().map(MavenBuildRunner::selectorFor).collect(Collectors.joining(","));
        // clean=false: this rerun happens on the same checkout the caller already built
        // once (run()) before any confirmFailure call — see the note on the private
        // command() overload for why skipping clean here is safe and, at N+1 rerun
        // scale, the difference between minutes and hours per candidate.
        return execute(projectDir, command(selector, relativeModulePath(projectDir, moduleDir), false), null, null);
    }

    private static String selectorFor(TestIdentity test) {
        return test.methodName() == null ? test.className() : test.className() + "#" + test.methodName();
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

    /**
     * Appends {@code -Dmaven.repo.local=<clone-scoped repo>} to {@code command} when this runner
     * was built with {@code isolatedRepo}, so the build resolves and locks dependencies inside a
     * private repo tied to {@code projectDir} (the clone) rather than the shared {@code ~/.m2}.
     * Left untouched otherwise, so single-build runs and every existing test keep the exact
     * argument list {@link #command} composes.
     */
    String[] withIsolatedRepo(String[] command, Path projectDir) {
        if (!isolatedRepo) {
            return command;
        }
        Path repo = CommitCheckout.isolatedMavenRepoFor(projectDir);
        String[] augmented = Arrays.copyOf(command, command.length + 1);
        augmented[command.length] = "-Dmaven.repo.local=" + repo;
        return augmented;
    }

    private BuildResult execute(Path projectDir, String[] command, Path agentJar, Path dependencyRecordOutputFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(withIsolatedRepo(command, projectDir))
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
                    awaitDescendantsWhileAlive(process, Duration.ofMinutes(timeoutMinutes));
            // A timed-out process is still alive here, so its stdout pipe is still open and
            // outputReader.join() below would block on it forever — the kill must happen
            // before the join, not after, or the deadline above never actually terminates
            // anything (found running 14+ hours past a 60-minute budget in practice).
            boolean timedOut = process.isAlive();
            if (timedOut) {
                process.destroyForcibly();
                descendants.forEach(ProcessHandle::destroyForcibly);
            }
            outputReader.join();

            if (timedOut) {
                throw new IllegalStateException(
                        "mvn test timed out against " + projectDir + " after " + timeoutMinutes + "m");
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
        return command(testSelector, null, true);
    }

    String[] command(String testSelector, String modulePath) {
        return command(testSelector, modulePath, true);
    }

    String[] command(String testSelector, String modulePath, boolean clean) {
        return command(testSelector, modulePath, clean, false);
    }

    String[] command(String testSelector, String modulePath, boolean clean, boolean alsoMakeDependents) {
        // `clean` is required for the FIRST build of a checkout, not cosmetic:
        // CommitCheckout reuses one scratch working copy across every commit in the
        // window, and `target/` is untracked — a previous commit's build artifacts
        // (including surefire-reports) would otherwise silently survive a `git
        // checkout` to a different commit. With modulePath set, -pl scopes `clean`
        // (and everything else below) to just the modules that are actually about to
        // be rebuilt.
        //
        // Single-test confirmFailure reruns (clean=false) happen back-to-back on the
        // SAME already-built checkout — no intervening `git checkout` — so skipping
        // clean there lets Maven's incremental compiler no-op on unchanged sources
        // instead of recompiling the whole module + its -am dependencies from scratch
        // on every one of the N+1 reruns. Surefire still overwrites each rerun test
        // class's own report file, so parsing stays correct without a clean.
        List<String> args = new ArrayList<>(List.of(MavenLauncher.resolve(), "-B", "--no-transfer-progress"));
        if (clean) {
            args.add("clean");
        }
        args.add("test");
        if (parallelThreads != null) {
            args.add("-T");
            args.add(String.valueOf(parallelThreads));
        }
        String effectiveSelector = skippedTests.appendTo(testSelector);
        if (effectiveSelector != null) {
            args.add("-Dtest=" + effectiveSelector);
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
            if (alsoMakeDependents) {
                // -amd (also-make-dependents): also build every module that depends on this one.
                // A single-file mutation in modulePath can break a test in a downstream module
                // that exercises the mutated code through modulePath's API; without -amd that
                // module is never built, so its (correctly selected) killing test never runs and
                // mutation validation would misreport it as a skipped killer (§III). Meaningless
                // for the single-test confirmFailure rerun (which names one test in one module),
                // so it stays off there.
                args.add("-amd");
            }
        }
        if (skipBuildExtras) {
            // Soundness-neutral: coverage/lint/license/resource-bundling plugins don't decide
            // which tests run or whether they pass (§III). Applied to single-test confirmFailure
            // reruns too — same rationale, and it keeps those reruns cheap.
            args.addAll(BUILD_EXTRA_SKIPS);
        }
        return args.toArray(new String[0]);
    }
}
