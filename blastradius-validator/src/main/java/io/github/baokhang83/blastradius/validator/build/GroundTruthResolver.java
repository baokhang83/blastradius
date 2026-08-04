package io.github.baokhang83.blastradius.validator.build;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a target project's full suite once (optionally with the tracking agent attached),
 * then re-runs each failed test once to confirm it isn't flaky (FR-013), producing a
 * {@link GroundTruthResult} per test with outcome {@code PASSED}, {@code CONFIRMED_FAILED},
 * or {@code FLAKY}.
 *
 * <p>Scans for {@code surefire-reports}/{@code failsafe-reports} directories anywhere
 * under the project root, not just at the root itself, so multi-module reactors (FR-011)
 * are handled without special-casing.
 */
public final class GroundTruthResolver {

    /**
     * Ceiling on the joined {@code -Dtest=} selector's length, in characters. Windows caps a command
     * line at ~32 KB and Linux caps the whole argument block at ~128 KB, so this leaves ample room
     * for the rest of the {@code mvn} invocation while still fitting the widest blast radius observed
     * (244 tests ≈ 12 KB) in a single rerun.
     */
    private static final int MAX_SELECTOR_LENGTH = 16_000;

    private final MavenBuildRunner buildRunner;
    private final SurefireReportParser reportParser;

    public GroundTruthResolver() {
        this(new MavenBuildRunner(), new SurefireReportParser());
    }

    /** Shares one {@link MavenBuildRunner} (e.g. its {@code -T} setting) with a caller's own. */
    public GroundTruthResolver(MavenBuildRunner buildRunner) {
        this(buildRunner, new SurefireReportParser());
    }

    GroundTruthResolver(MavenBuildRunner buildRunner, SurefireReportParser reportParser) {
        this.buildRunner = buildRunner;
        this.reportParser = reportParser;
    }

    public GroundTruthResolution resolve(Path projectDir, Path agentJar, Path dependencyRecordOutputFile) {
        return resolve(projectDir, agentJar, dependencyRecordOutputFile, null);
    }

    /**
     * Like {@link #resolve(Path, Path, Path)}, but scopes the initial full-suite build to
     * {@code modulePath} (plus its upstream dependencies and downstream dependents) instead of the
     * whole reactor — see {@link MavenBuildRunner#run(Path, Path, Path, String)}. The confirmation
     * reruns for any failures found stay per-test-module-scoped as before, since a failing test's
     * own module is the tighter scope. A {@code null} {@code modulePath} builds the whole reactor,
     * so this is a strict superset of the three-arg overload.
     */
    public GroundTruthResolution resolve(
            Path projectDir, Path agentJar, Path dependencyRecordOutputFile, String modulePath) {
        BuildResult initialBuild = buildRunner.run(projectDir, agentJar, dependencyRecordOutputFile, modulePath);
        Map<TestIdentity, Path> moduleByTest = new HashMap<>();
        Map<TestIdentity, Boolean> initialResults = parseAllReports(projectDir, moduleByTest);

        Map<TestIdentity, Outcome> confirmed = confirmFailures(projectDir, initialResults, moduleByTest);
        List<GroundTruthResult> results = new ArrayList<>();
        for (Map.Entry<TestIdentity, Boolean> entry : initialResults.entrySet()) {
            TestIdentity test = entry.getKey();
            results.add(new GroundTruthResult(
                    test, entry.getValue() ? Outcome.PASSED : confirmed.get(test)));
        }
        return new GroundTruthResolution(initialBuild, results);
    }

    /**
     * Re-runs every failure to tell a genuine failure apart from a flaky one (FR-013), batching them
     * so the number of {@code mvn} invocations tracks the number of <em>modules</em> that failed, not
     * the number of failing tests. Verdicts are unchanged: each named test still runs, and each is
     * still judged only by its own freshly-overwritten report.
     *
     * <p>Batching is what makes mutation validation tractable. A mutant in a low-level class fails a
     * wide slice of the suite (~27 tests on average, 244 at the extreme on jsoup), and one {@code mvn}
     * per failure meant tens of minutes per mutant.
     *
     * @return the confirmed outcome for each failing test in {@code initialResults}; passing tests
     *         are absent, since they are never rerun
     */
    private Map<TestIdentity, Outcome> confirmFailures(
            Path projectDir, Map<TestIdentity, Boolean> initialResults, Map<TestIdentity, Path> moduleByTest) {
        // Grouped by module because a batch becomes one -pl-scoped build: mixing modules would
        // either widen the scope to the whole reactor or drop the tests outside it. LinkedHashMap
        // (and the sort within each group) keeps the rerun order a function of the failures alone,
        // so a re-run of the same window issues the same builds in the same order (§IV).
        Map<Path, List<TestIdentity>> failuresByModule = new LinkedHashMap<>();
        initialResults.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(TestIdentity::className)
                        .thenComparing(TestIdentity::methodName, Comparator.nullsFirst(Comparator.naturalOrder())))
                .forEach(test -> failuresByModule
                        .computeIfAbsent(moduleByTest.get(test), module -> new ArrayList<>())
                        .add(test));

        Map<TestIdentity, Outcome> confirmed = new HashMap<>();
        failuresByModule.forEach((moduleDir, failures) -> {
            for (List<TestIdentity> batch : batched(failures)) {
                buildRunner.runTests(projectDir, batch, moduleDir);
                Map<TestIdentity, Boolean> rerunResults = parseAllReports(projectDir, new HashMap<>());
                for (TestIdentity test : batch) {
                    // Absent from the rerun's reports means the test never ran (e.g. its module
                    // failed to compile), which is not evidence of flakiness — treat it as the
                    // failure the full run already observed (§III).
                    confirmed.put(test, Boolean.TRUE.equals(rerunResults.get(test))
                            ? Outcome.FLAKY
                            : Outcome.CONFIRMED_FAILED);
                }
            }
        });
        return confirmed;
    }

    /**
     * Splits {@code failures} into batches whose joined {@code -Dtest=} selector stays within
     * {@link #MAX_SELECTOR_LENGTH}. Without a bound, a wide mutant's 244-test selector would build a
     * command line past the OS argument limit and the rerun would fail outright rather than run
     * slowly — so the cap is a correctness guard, not a tuning knob. Even at the extreme it costs a
     * handful of invocations instead of one per failure.
     */
    private static List<List<TestIdentity>> batched(List<TestIdentity> failures) {
        List<List<TestIdentity>> batches = new ArrayList<>();
        List<TestIdentity> current = new ArrayList<>();
        int length = 0;
        for (TestIdentity test : failures) {
            int cost = test.className().length()
                    + (test.methodName() == null ? 0 : test.methodName().length() + 1) + 1;
            if (!current.isEmpty() && length + cost > MAX_SELECTOR_LENGTH) {
                batches.add(current);
                current = new ArrayList<>();
                length = 0;
            }
            current.add(test);
            length += cost;
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }

    /**
     * @param moduleByTest populated (as a side effect) with each discovered test's
     *                     containing module root, so a later {@link #confirmFailure} can
     *                     scope its rerun instead of walking the whole reactor
     */
    private Map<TestIdentity, Boolean> parseAllReports(Path projectDir, Map<TestIdentity, Path> moduleByTest) {
        Map<TestIdentity, Boolean> merged = new HashMap<>();
        for (Path reportsDir : findReportsDirectories(projectDir)) {
            Map<TestIdentity, Boolean> parsed = reportParser.parse(reportsDir);
            merged.putAll(parsed);
            // <module>/target/surefire-reports -> target -> module root.
            Path moduleDir = reportsDir.getParent().getParent();
            parsed.keySet().forEach(test -> moduleByTest.put(test, moduleDir));
        }
        return merged;
    }

    private static List<Path> findReportsDirectories(Path projectDir) {
        List<Path> reportDirs = new ArrayList<>();
        try {
            // walkFileTree pruning .git: reports live under target/, so that can't be skipped,
            // but .git holds no reports and (on a big repo) is the largest tree — not descending
            // it is the win. This scan runs after every build AND every confirmFailure rerun,
            // so its cost is paid many times per pair.
            Files.walkFileTree(projectDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (".git".equals(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (name.equals("surefire-reports") || name.equals("failsafe-reports")) {
                        reportDirs.add(dir);
                        // A reports dir contains only report files — no nested reports dir — so
                        // there's nothing to gain from descending into it.
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("failed to scan for test reports under " + projectDir, e);
        }
        return reportDirs;
    }
}
