package io.github.baokhang83.blastradius.validator.cli;

import io.github.baokhang83.blastradius.validator.build.BuildFailureDetector;
import io.github.baokhang83.blastradius.validator.build.BuildResult;
import io.github.baokhang83.blastradius.validator.build.GroundTruthResolution;
import io.github.baokhang83.blastradius.validator.build.GroundTruthResolver;
import io.github.baokhang83.blastradius.validator.build.GroundTruthResult;
import io.github.baokhang83.blastradius.validator.build.JdkMismatchDetector;
import io.github.baokhang83.blastradius.validator.build.MavenBuildRunner;
import io.github.baokhang83.blastradius.validator.build.Outcome;
import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.ChangedFileClassifier;
import io.github.baokhang83.blastradius.core.reactor.ReactorModuleGraph;
import io.github.baokhang83.blastradius.core.reactor.ReactorModuleGraphBuilder;
import io.github.baokhang83.blastradius.core.reactor.ReactorScope;
import io.github.baokhang83.blastradius.core.reactor.TestModuleIndex;
import io.github.baokhang83.blastradius.validator.git.CommitCheckout;
import io.github.baokhang83.blastradius.validator.git.CommitPair;
import io.github.baokhang83.blastradius.validator.git.CommitWindowResolver;
import io.github.baokhang83.blastradius.validator.git.PairStatus;
import io.github.baokhang83.blastradius.validator.report.AnalysisReport;
import io.github.baokhang83.blastradius.validator.report.ReportWriter;
import io.github.baokhang83.blastradius.validator.report.SavingsSummary;
import io.github.baokhang83.blastradius.validator.report.SavingsSummaryAggregator;
import io.github.baokhang83.blastradius.core.selection.NewOrModifiedTestSelector;
import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.selection.SelectionEngine;
import io.github.baokhang83.blastradius.core.tracking.DependencyRecordReader;
import io.github.baokhang83.blastradius.core.tracking.DependencyRecordSet;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.validator.verdict.FlakyFailure;
import io.github.baokhang83.blastradius.validator.verdict.Verdict;
import io.github.baokhang83.blastradius.validator.verdict.VerdictCalculator;
import io.github.baokhang83.blastradius.validator.verdict.WouldMissCase;
import io.github.baokhang83.blastradius.validator.verdict.WouldMissComparator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wires the full pipeline together: for each commit pair in the resolved window, checks
 * out the base commit to obtain a dependency baseline, checks out the head commit to
 * obtain ground truth, classifies changes, runs selection, and compares against ground
 * truth for would-miss cases — then computes the overall verdict and writes the report.
 * A pair whose base or head commit fails to build — or whose analysis fails for any
 * other reason (e.g. the tracking agent produced no output for a commit whose build
 * otherwise succeeded) — is excluded (FR-009) rather than aborting the whole run.
 *
 * <p>Exit codes per the CLI contract: {@code 0} = PASS, {@code 1} = FAIL, {@code 2} =
 * the run itself could not complete.
 */
public final class RunCommand {

    private final CommitWindowResolver commitWindowResolver = new CommitWindowResolver();
    private final ChangedFileClassifier changedFileClassifier = new ChangedFileClassifier();
    private final ReactorModuleGraphBuilder reactorModuleGraphBuilder = new ReactorModuleGraphBuilder();
    private final SelectionEngine selectionEngine = new SelectionEngine();
    private final NewOrModifiedTestSelector newOrModifiedTestSelector = new NewOrModifiedTestSelector();
    private final WouldMissComparator wouldMissComparator = new WouldMissComparator();
    private final VerdictCalculator verdictCalculator = new VerdictCalculator();
    private final SavingsSummaryAggregator savingsSummaryAggregator = new SavingsSummaryAggregator();
    private final ReportWriter reportWriter = new ReportWriter();
    private final BuildFailureDetector buildFailureDetector = new BuildFailureDetector();
    private final JdkMismatchDetector jdkMismatchDetector = new JdkMismatchDetector();

    // Set at the start of each run() from RunConfig#mavenParallelThreads, so the base/head
    // builds and GroundTruthResolver's own build all share one -T setting for that run.
    private MavenBuildRunner buildRunner = new MavenBuildRunner();
    private GroundTruthResolver groundTruthResolver = new GroundTruthResolver();

    /** Runs with this tool's own jar self-located for {@code -javaagent} attachment. */
    public int run(RunConfig config) {
        return run(config, locateOwnJar());
    }

    /**
     * Runs with an explicitly-supplied agent jar path, instead of self-locating one.
     * Also the seam integration tests use, since self-location only resolves to a real
     * jar when running from one — not from {@code target/classes} during our own
     * {@code mvn test}.
     */
    public int run(RunConfig config, Path agentJar) {
        try {
            buildRunner = new MavenBuildRunner(config.mavenParallelThreads());
            groundTruthResolver = new GroundTruthResolver(buildRunner);

            jdkMismatchDetector.detect(config.projectPath()).ifPresent(System.err::println);

            List<CommitPair> window =
                    commitWindowResolver.resolveWindow(config.projectPath(), config.commitWindowSize());

            List<CommitPair> analyzedPairs = new ArrayList<>();
            List<CommitPair> excludedPairs = new ArrayList<>();
            List<WouldMissCase> allMisses = new ArrayList<>();
            List<SelectionDecision> allDecisions = new ArrayList<>();
            List<FlakyFailure> allFlaky = new ArrayList<>();
            // Only ever populated when config.fastGroundTruth() is true (§III — see
            // RunConfig#fastGroundTruth): the safe, default path never unifies build types
            // across pairs, so there is nothing to cache.
            Map<String, CommitBuild> commitCache = new HashMap<>();

            Path scratchParent = Files.createTempDirectory("blastradius-scratch-");
            try (CommitCheckout checkout = CommitCheckout.forTargetProject(config.projectPath(), scratchParent)) {
                for (CommitPair pair : window) {
                    PairAnalysis analysis;
                    try {
                        analysis = analyzePair(
                                pair, config.projectPath(), checkout, agentJar, config.fastGroundTruth(), commitCache);
                    } catch (Exception e) {
                        analysis = excluded(pair, "analysis failed: " + e.getMessage());
                    }
                    if (analysis.pair().status() == PairStatus.EXCLUDED) {
                        excludedPairs.add(analysis.pair());
                    } else {
                        analyzedPairs.add(analysis.pair());
                        allMisses.addAll(analysis.misses());
                        allDecisions.addAll(analysis.decisions());
                        allFlaky.addAll(analysis.flakyFailures());
                    }
                }
            }

            Verdict verdict = verdictCalculator.calculate(allMisses);
            SavingsSummary savingsSummary = savingsSummaryAggregator.aggregate(allDecisions);
            AnalysisReport report = new AnalysisReport(
                    verdict, analyzedPairs, excludedPairs, allMisses, allFlaky, savingsSummary);
            reportWriter.write(config.reportOutputPath(), report);

            return verdict == Verdict.PASS ? 0 : 1;
        } catch (Exception e) {
            System.err.println("blastradius-validator: " + e.getMessage());
            return 2;
        }
    }

    private record PairAnalysis(
            CommitPair pair,
            List<WouldMissCase> misses,
            List<SelectionDecision> decisions,
            List<FlakyFailure> flakyFailures) {}

    /**
     * One commit's canonical build outcome under {@code --fast-ground-truth} (RunConfig
     * #fastGroundTruth): a single agent-attached build serving both the "dependency
     * baseline" role and the "ground truth" role, cached across every pair in the window
     * that references this commit. Never populated in the safe, default mode.
     */
    private record CommitBuild(
            boolean failed, String failureReason, DependencyRecordSet dependencyRecordSet,
            List<GroundTruthResult> groundTruth) {}

    private PairAnalysis analyzePair(
            CommitPair pair, Path targetRepo, CommitCheckout checkout, Path agentJar,
            boolean fastGroundTruth, Map<String, CommitBuild> commitCache) throws Exception {
        DependencyRecordSet baseRecordSet;
        List<GroundTruthResult> groundTruth;

        if (fastGroundTruth) {
            CommitBuild base = buildCommit(pair.baseCommit(), checkout, agentJar, commitCache);
            if (base.failed()) {
                return excluded(pair, base.failureReason());
            }
            CommitBuild head = buildCommit(pair.headCommit(), checkout, agentJar, commitCache);
            if (head.failed()) {
                return excluded(pair, head.failureReason());
            }
            baseRecordSet = base.dependencyRecordSet();
            groundTruth = head.groundTruth();
        } else {
            // Baseline: build the BASE commit with the agent attached, to learn what each
            // test depended on as of that commit.
            Path baseWorkDir = checkout.checkoutCommit(pair.baseCommit());
            Path baseDepsFile = Files.createTempFile("blastradius-base-deps-", ".json");
            BuildResult baseResult = buildRunner.run(baseWorkDir, agentJar, baseDepsFile);
            if (buildFailureDetector.isBuildFailure(baseResult, baseWorkDir)) {
                return excluded(pair, "base commit " + pair.baseCommit() + " failed to build");
            }
            baseRecordSet = new DependencyRecordReader().readAll(baseDepsFile);

            // Ground truth: GroundTruthResolver's own build result tells us whether the
            // HEAD commit built at all, so a compile failure is caught here rather than
            // silently looking like "zero tests" — without a second, separate probe build.
            Path headWorkDir = checkout.checkoutCommit(pair.headCommit());
            GroundTruthResolution resolution = groundTruthResolver.resolve(headWorkDir, null, null);
            if (buildFailureDetector.isBuildFailure(resolution.initialBuild(), headWorkDir)) {
                return excluded(pair, "head commit " + pair.headCommit() + " failed to build");
            }
            groundTruth = resolution.results();
        }

        Map<TestIdentity, Map<String, String>> baseRecord = baseRecordSet.tests();
        // Keyed by baselineKey(), not the raw tracked identity, so a ground-truth lookup
        // (which may carry a parameterized-test's Surefire-style invocation suffix) can
        // still find the baseline the tracking agent recorded under the plain method
        // name. See TestIdentity#baselineKey for why the two pipelines disagree here.
        Map<TestIdentity, Set<String>> testDependencies = baseRecord.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().baselineKey(),
                        e -> e.getValue().keySet(),
                        (a, b) -> {
                            Set<String> union = new java.util.HashSet<>(a);
                            union.addAll(b);
                            return union;
                        }));

        // Changed files, classified, against the real target repo's git history.
        List<ChangedFile> changedFiles =
                changedFileClassifier.classify(targetRepo, pair.baseCommit(), pair.headCommit());
        Set<String> changedClassNames = changedFiles.stream()
                .flatMap(file -> file.candidateClassNames().stream())
                .collect(Collectors.toUnmodifiableSet());

        Set<TestIdentity> allTests = groundTruth.stream().map(GroundTruthResult::test).collect(Collectors.toSet());
        Set<TestIdentity> newOrModifiedTests = allTests.stream()
                .filter(test -> newOrModifiedTestSelector.appliesTo(
                        test, !testDependencies.containsKey(test.baselineKey()), changedClassNames))
                .collect(Collectors.toSet());

        // Reactor scope for the NON_SOURCE fallback: built from the HEAD tree so module layout
        // and inter-module edges reflect the commit selection runs against. Explicitly re-check
        // out head — in --fast-ground-truth mode the scratch clone may still reflect base (a
        // cached head build skips its checkout), and building the graph from the wrong tree
        // could silently mis-scope. The build results are already extracted, so wiping target/
        // here is harmless; a git checkout is negligible next to a Maven build.
        ReactorScope reactorScope = buildReactorScope(checkout, pair.headCommit());

        List<SelectionDecision> decisions = selectionEngine.selectAll(
                allTests, testDependencies, newOrModifiedTests, changedFiles,
                baseRecordSet.ambientDependencies(), reactorScope);

        CommitPair enrichedPair = CommitPair.analyzed(pair.baseCommit(), pair.headCommit(), changedFiles);
        List<WouldMissCase> misses = wouldMissComparator.compare(enrichedPair, decisions, groundTruth);
        List<FlakyFailure> flakyFailures = groundTruth.stream()
                .filter(r -> r.outcome() == Outcome.FLAKY)
                .map(r -> new FlakyFailure(enrichedPair, r.test()))
                .toList();

        return new PairAnalysis(enrichedPair, misses, decisions, flakyFailures);
    }

    /**
     * {@code --fast-ground-truth} only: one canonical, agent-attached build per commit,
     * memoized in {@code commitCache} for the lifetime of this {@code run()} call. A cache
     * hit means the commit was already checked out and built by an earlier pair — it is
     * <em>not</em> re-checked-out (see {@link CommitCheckout}, which wipes {@code target/}
     * on every checkout), so a hit returns the results extracted the first time, never a
     * stale working directory.
     */
    private CommitBuild buildCommit(
            String commitSha, CommitCheckout checkout, Path agentJar, Map<String, CommitBuild> commitCache) {
        return commitCache.computeIfAbsent(commitSha, sha -> {
            Path workDir = checkout.checkoutCommit(sha);
            Path depsFile;
            try {
                depsFile = Files.createTempFile("blastradius-commit-deps-", ".json");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            GroundTruthResolution resolution = groundTruthResolver.resolve(workDir, agentJar, depsFile);
            if (buildFailureDetector.isBuildFailure(resolution.initialBuild(), workDir)) {
                return new CommitBuild(true, "commit " + sha + " failed to build", null, List.of());
            }
            DependencyRecordSet recordSet = new DependencyRecordReader().readAll(depsFile);
            return new CommitBuild(false, null, recordSet, resolution.results());
        });
    }

    /**
     * Builds the reactor scope from {@code headCommit}'s tree. Best-effort: if the tree can't
     * be parsed into a graph (a malformed or unusual POM in some historical commit), returns
     * {@code null} so selection falls back to the safe whole-suite behavior rather than
     * aborting the pair — never a narrower scope on uncertainty (Constitution §III).
     */
    private ReactorScope buildReactorScope(CommitCheckout checkout, String headCommit) {
        try {
            Path headTree = checkout.checkoutCommit(headCommit);
            ReactorModuleGraph graph = reactorModuleGraphBuilder.fromRepoTree(headTree);
            return new ReactorScope(graph, TestModuleIndex.fromRepoTree(headTree, graph));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static PairAnalysis excluded(CommitPair pair, String reason) {
        CommitPair excludedPair = CommitPair.excluded(pair.baseCommit(), pair.headCommit(), reason);
        return new PairAnalysis(excludedPair, List.of(), List.of(), List.of());
    }

    private static Path locateOwnJar() {
        try {
            URI uri = RunCommand.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            return Path.of(uri);
        } catch (Exception e) {
            throw new IllegalStateException("could not locate this tool's own jar for -javaagent attachment", e);
        }
    }
}
