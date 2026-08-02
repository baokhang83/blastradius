package io.github.baokhang83.blastradius.validator.cli;

import io.github.baokhang83.blastradius.validator.build.BuildCache;
import io.github.baokhang83.blastradius.validator.build.BuildFailureDetector;
import io.github.baokhang83.blastradius.validator.build.BuildOutcome;
import io.github.baokhang83.blastradius.validator.build.CheckoutPool;
import io.github.baokhang83.blastradius.validator.build.CommitBuild;
import io.github.baokhang83.blastradius.validator.build.CommitBuildService;
import io.github.baokhang83.blastradius.validator.build.CommitBuildService.BuildKey;
import io.github.baokhang83.blastradius.validator.build.GroundTruthResolution;
import io.github.baokhang83.blastradius.validator.build.GroundTruthResolver;
import io.github.baokhang83.blastradius.validator.build.GroundTruthResult;
import io.github.baokhang83.blastradius.validator.build.JdkMismatchDetector;
import io.github.baokhang83.blastradius.validator.build.MavenBuildRunner;
import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.ChangedFileClassifier;
import io.github.baokhang83.blastradius.core.reactor.ReactorModuleGraph;
import io.github.baokhang83.blastradius.core.reactor.ReactorModuleGraphBuilder;
import io.github.baokhang83.blastradius.core.reactor.ReactorScope;
import io.github.baokhang83.blastradius.core.reactor.TestModuleIndex;
import io.github.baokhang83.blastradius.validator.git.CommitCheckout;
import io.github.baokhang83.blastradius.validator.git.CommitPair;
import io.github.baokhang83.blastradius.validator.git.CommitWindowResolver;
import io.github.baokhang83.blastradius.validator.selection.PairSelectionAnalyzer;
import io.github.baokhang83.blastradius.validator.selection.PairSelectionResult;
import io.github.baokhang83.blastradius.validator.report.FailureCoverage;
import io.github.baokhang83.blastradius.validator.git.PairStatus;
import io.github.baokhang83.blastradius.validator.mutation.HistoricalMutationValidator;
import io.github.baokhang83.blastradius.validator.mutation.MutationExperiment;
import io.github.baokhang83.blastradius.validator.mutation.MutationValidationReport;
import io.github.baokhang83.blastradius.validator.report.AnalysisReport;
import io.github.baokhang83.blastradius.validator.report.ReportWriter;
import io.github.baokhang83.blastradius.validator.report.SavingsSummary;
import io.github.baokhang83.blastradius.validator.report.SavingsSummaryAggregator;
import io.github.baokhang83.blastradius.core.selection.FallbackSelector;
import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.tracking.DependencyRecordReader;
import io.github.baokhang83.blastradius.core.tracking.DependencyRecordSet;
import io.github.baokhang83.blastradius.validator.verdict.FlakyFailure;
import io.github.baokhang83.blastradius.validator.verdict.Verdict;
import io.github.baokhang83.blastradius.validator.verdict.VerdictCalculator;
import io.github.baokhang83.blastradius.validator.verdict.WouldMissCase;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final FallbackSelector fallbackSelector = new FallbackSelector();
    private final VerdictCalculator verdictCalculator = new VerdictCalculator();
    private final SavingsSummaryAggregator savingsSummaryAggregator = new SavingsSummaryAggregator();
    private final PairSelectionAnalyzer pairSelectionAnalyzer = new PairSelectionAnalyzer();
    private final ReportWriter reportWriter = new ReportWriter();
    private final BuildFailureDetector buildFailureDetector = new BuildFailureDetector();
    private final JdkMismatchDetector jdkMismatchDetector = new JdkMismatchDetector();
    private final ProgressLogger progress;

    public RunCommand() {
        this(ProgressLogger.toStderr());
    }

    RunCommand(ProgressLogger progress) {
        this.progress = progress;
    }

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
            // Isolate each build's local Maven repo only when builds actually run concurrently:
            // it's what removes the ~/.m2 file-lock contention that serializes parallel builds
            // (apache/shenyu's maven-remote-resources-plugin "Could not acquire lock(s)"), and it
            // costs a one-time dependency download per clone, so there's no reason to pay it when
            // build-concurrency is 1.
            boolean isolatedRepo = config.buildConcurrency() > 1;
            buildRunner = new MavenBuildRunner(
                    config.mavenParallelThreads(), config.buildTimeoutMinutes(), isolatedRepo,
                    config.skipBuildExtras(), config.skippedTests());
            groundTruthResolver = new GroundTruthResolver(buildRunner);

            jdkMismatchDetector.detect(config.projectPath()).ifPresent(System.err::println);

            long runStart = System.currentTimeMillis();
            List<CommitPair> window =
                    commitWindowResolver.resolveWindow(
                            config.projectPath(), config.commitWindowSize(), config.historyMode());
            progress.windowResolved(window.size());

            int concurrency = config.buildConcurrency();
            Path scratchParent = Files.createTempDirectory("blastradius-scratch-");
            ExecutorService executor = Executors.newFixedThreadPool(concurrency);
            // The build cache lives beside the report so it is stable across re-runs (its whole
            // point is resume): a run that dies partway leaves its successful builds on disk, and
            // the next invocation with the same --report-out skips straight past them. It also
            // bounds phase 1's heap — each build is written here and evicted from memory rather
            // than accumulated for the whole window (the OOM this fixes).
            BuildCache cache = new BuildCache(buildCacheDirectory(config.reportOutputPath()));
            try (CheckoutPool pool = CheckoutPool.of(config.projectPath(), scratchParent, concurrency)) {
                // Phase 1 (expensive, now core-saturated): build every commit the window needs,
                // concurrently across the pool of isolated clones, persisting each to the cache.
                Map<BuildKey, BuildOutcome> outcomes =
                        buildAllCommits(window, config.fastGroundTruth(), agentJar, pool, executor, cache);

                // Phase 2 (cheap, serial): select + compare per pair, loading each build from the
                // cache on demand so the full payloads are never all resident at once.
                long mutationDeadlineNanos = config.mutationValidation() == null ? Long.MAX_VALUE
                        : System.nanoTime() + Duration.ofMinutes(config.mutationValidation().timeLimitMinutes()).toNanos();
                AnalysisReport report = analyzeWindow(window, config, outcomes, cache, pool, mutationDeadlineNanos);
                reportWriter.write(config.reportOutputPath(), report);

                progress.summary(report.verdict().name(), System.currentTimeMillis() - runStart);
                return switch (report.verdict()) {
                    case PASS -> 0;
                    case FAIL -> 1;
                    // Distinct from 2, which means the tool itself errored (see the catch below):
                    // this is a clean run that simply never confirmed a killed mutant.
                    case INCONCLUSIVE -> 3;
                };
            } finally {
                executor.shutdownNow();
            }
        } catch (Exception e) {
            System.err.println("blastradius-validator: " + e.getMessage());
            return 2;
        }
    }

    /**
     * Phase 1: enumerates the distinct builds the whole window needs and runs them
     * concurrently. The base commit of every pair is always built with the agent attached
     * (to record the dependency baseline). The head commit is built with the agent in
     * {@code --fast-ground-truth} mode (reused as both baseline and ground truth) but
     * <em>without</em> it in the safe default mode, so ground truth stays independent of the
     * tracking agent (§III). Because a commit that is the head of one pair is the base of the
     * next, the default mode legitimately builds that commit twice — once with the agent
     * (as a base) and once without (as a head) — which distinct {@link BuildKey}s capture,
     * while fast mode collapses them to a single agent-attached build.
     */
    private Map<BuildKey, BuildOutcome> buildAllCommits(
            List<CommitPair> window, boolean fastGroundTruth, Path agentJar,
            CheckoutPool pool, ExecutorService executor, BuildCache cache) throws InterruptedException {
        List<BuildKey> keys = new ArrayList<>();
        for (CommitPair pair : window) {
            keys.add(new BuildKey(pair.baseCommit(), true));
            keys.add(new BuildKey(pair.headCommit(), fastGroundTruth));
        }
        CommitBuildService service = new CommitBuildService(
                pool, executor, progress, cache,
                (checkout, sha, agentAttached) -> buildCommit(checkout, sha, agentAttached, agentJar));
        return service.buildAll(keys);
    }

    /**
     * Phase 2: the cheap per-pair selection + comparison, run serially over the already-built
     * commits. Holds a single borrowed clone for the whole phase, used only to materialize a
     * head tree when a pair actually needs a reactor scope (a NON_SOURCE change); since phase 1
     * has finished, every clone is idle, so this borrow never blocks.
     */
    private AnalysisReport analyzeWindow(
            List<CommitPair> window, RunConfig config, Map<BuildKey, BuildOutcome> outcomes,
            BuildCache cache, CheckoutPool pool, long mutationDeadlineNanos)
            throws InterruptedException {
        List<CommitPair> analyzedPairs = new ArrayList<>();
        List<CommitPair> excludedPairs = new ArrayList<>();
        List<WouldMissCase> allMisses = new ArrayList<>();
        List<SelectionDecision> allDecisions = new ArrayList<>();
        List<FlakyFailure> allFlaky = new ArrayList<>();
        FailureCoverage failureCoverage = FailureCoverage.empty();
        List<MutationExperiment> mutationExperiments = new ArrayList<>();
        int generatedMutations = 0;
        int timeLimitSkippedMutations = 0;
        HistoricalMutationValidator mutationValidator = config.mutationValidation() == null
                ? null : new HistoricalMutationValidator(groundTruthResolver);
        // Reactor scope per HEAD commit: a repeated head across the window repays neither the
        // checkout nor the tree walk. Only populated for pairs with a NON_SOURCE change.
        Map<String, ReactorScope> reactorScopeCache = new HashMap<>();

        CommitCheckout scopeCheckout = pool.borrow();
        try {
            int pairIndex = 0;
            for (CommitPair pair : window) {
                pairIndex++;
                long pairStart = System.currentTimeMillis();
                PairAnalysis analysis;
                try {
                    analysis = analyzePair(
                            pair, config.projectPath(), outcomes, cache, config.fastGroundTruth(),
                            scopeCheckout, reactorScopeCache);
                } catch (Exception e) {
                    analysis = excluded(pair, "analysis failed: " + e.getMessage());
                }
                if (analysis.pair().status() != PairStatus.EXCLUDED && mutationValidator != null) {
                    try {
                        CommitBuild base = cache.load(new BuildKey(pair.baseCommit(), true)).orElseThrow();
                        CommitBuild head = cache.load(new BuildKey(pair.headCommit(), config.fastGroundTruth())).orElseThrow();
                        HistoricalMutationValidator.PairMutationResult mutation = mutationValidator.validate(
                                pair, base, head, scopeCheckout, config.mutationValidation(), mutationDeadlineNanos);
                        mutationExperiments.addAll(mutation.experiments());
                        generatedMutations += mutation.generated();
                        timeLimitSkippedMutations += mutation.timeLimitSkipped();
                    } catch (Exception e) {
                        analysis = excluded(pair, "mutation validation failed: " + e.getMessage());
                    }
                }
                long pairMillis = System.currentTimeMillis() - pairStart;
                if (analysis.pair().status() == PairStatus.EXCLUDED) {
                    excludedPairs.add(analysis.pair());
                    progress.pairExcluded(pairIndex, window.size(), analysis.pair().exclusionReason());
                } else {
                    analyzedPairs.add(analysis.pair());
                    allMisses.addAll(analysis.misses());
                    allDecisions.addAll(analysis.decisions());
                    allFlaky.addAll(analysis.flakyFailures());
                    failureCoverage = failureCoverage.plus(analysis.failureCoverage());
                    progress.pairCompleted(pairIndex, window.size(), analysis.misses().size(), pairMillis);
                }
            }
        } finally {
            pool.release(scopeCheckout);
        }

        Verdict historyVerdict = verdictCalculator.calculate(allMisses);
        MutationValidationReport mutationValidation = mutationValidator == null ? null
                : MutationValidationReport.from(mutationExperiments, generatedMutations, timeLimitSkippedMutations);
        // Precedence FAIL > INCONCLUSIVE > PASS: a confirmed miss always wins regardless of how
        // much mutation evidence backs it, but absent that, an INCONCLUSIVE mutation leg (no
        // mutant was actually killed) must not be swallowed into an unearned overall PASS (§III).
        Verdict verdict;
        if (historyVerdict == Verdict.FAIL || mutationValidation != null && mutationValidation.verdict() == Verdict.FAIL) {
            verdict = Verdict.FAIL;
        } else if (mutationValidation != null && mutationValidation.verdict() == Verdict.INCONCLUSIVE) {
            verdict = Verdict.INCONCLUSIVE;
        } else {
            verdict = Verdict.PASS;
        }
        SavingsSummary savingsSummary = savingsSummaryAggregator.aggregate(allDecisions);
        return new AnalysisReport(verdict, config.historyMode(), analyzedPairs, excludedPairs, failureCoverage,
                allMisses, allFlaky, savingsSummary, config.skippedTests().classes(), mutationValidation);
    }

    private record PairAnalysis(
            CommitPair pair,
            List<WouldMissCase> misses,
            FailureCoverage failureCoverage,
            List<SelectionDecision> decisions,
            List<FlakyFailure> flakyFailures) {}

    private PairAnalysis analyzePair(
            CommitPair pair, Path targetRepo, Map<BuildKey, BuildOutcome> outcomes, BuildCache cache,
            boolean fastGroundTruth,
            CommitCheckout scopeCheckout, Map<String, ReactorScope> reactorScopeCache) throws Exception {
        // The base commit is always the agent-attached build; the head build's agent flag
        // matches how phase 1 enumerated its key (with in fast mode, without in safe mode).
        // Phase 1 returned only a pass/fail marker per key; the heavy payload is loaded from the
        // cache here, on demand, so the whole window's builds are never all resident at once.
        BuildKey baseKey = new BuildKey(pair.baseCommit(), true);
        if (outcomes.get(baseKey).failed()) {
            return excluded(pair, outcomes.get(baseKey).failureReason());
        }
        BuildKey headKey = new BuildKey(pair.headCommit(), fastGroundTruth);
        if (outcomes.get(headKey).failed()) {
            return excluded(pair, outcomes.get(headKey).failureReason());
        }
        CommitBuild base = cache.load(baseKey)
                .orElseThrow(() -> new IllegalStateException(
                        "build cache miss for " + pair.baseCommit() + " despite a successful build outcome"));
        CommitBuild head = cache.load(headKey)
                .orElseThrow(() -> new IllegalStateException(
                        "build cache miss for " + pair.headCommit() + " despite a successful build outcome"));
        DependencyRecordSet baseRecordSet = base.dependencyRecordSet();
        List<GroundTruthResult> baseGroundTruth = base.groundTruth();
        List<GroundTruthResult> groundTruth = head.groundTruth();

        // Changed files, classified, against the real target repo's git history.
        List<ChangedFile> changedFiles =
                changedFileClassifier.classify(targetRepo, pair.baseCommit(), pair.headCommit());

        // Reactor scope for the NON_SOURCE fallback: built from the HEAD tree so module layout
        // and inter-module edges reflect the commit selection runs against. Phase 1's concurrent
        // builds don't retain their working trees, so here the head commit is re-materialized on
        // the phase-2 scopeCheckout, memoized per commit so a head repeated across the window
        // repays neither the checkout nor the tree walk.
        //
        // Only built when this pair actually has a NON_SOURCE change: SelectionEngine consults
        // the scope solely inside its fallback branch, so on a source-only pair (the common case)
        // the two full-tree scans would be pure waste — a null scope is behaviorally identical
        // there (see SelectionEngine#selectAll).
        ReactorScope reactorScope = null;
        if (fallbackSelector.shouldFallback(changedFiles)) {
            reactorScope = reactorScopeCache.computeIfAbsent(
                    pair.headCommit(), sha -> buildReactorScope(scopeCheckout.checkoutCommit(sha)));
        }

        CommitPair enrichedPair = CommitPair.analyzed(pair.baseCommit(), pair.headCommit(), changedFiles);
        PairSelectionResult result = pairSelectionAnalyzer.analyze(
                enrichedPair, baseRecordSet, baseGroundTruth, groundTruth, reactorScope);
        return new PairAnalysis(enrichedPair, result.failureComparison().wouldMissCases(),
                result.failureComparison().coverage(), result.decisions(), result.flakyFailures());
    }

    /**
     * The per-build worker for phase 1, invoked once per distinct {@link BuildKey} on a clone
     * the {@link CheckoutPool} has already handed the caller. Checks out {@code sha} on that
     * clone (which wipes {@code target/} — §VII) and runs the suite through
     * {@link GroundTruthResolver} so a test already broken at this commit is confirmed and
     * recorded, which the shared pair-selection analysis needs to avoid flagging a pre-existing
     * failure as a miss.
     *
     * <p>When {@code agentAttached} is true the tracking agent records each test's
     * dependencies (the baseline); when false it is an independent ground-truth build with no
     * agent (§III). A build failure becomes a {@linkplain CommitBuild#failed(String) failed}
     * result rather than an exception, so the referencing pair is excluded (FR-009).
     */
    private CommitBuild buildCommit(CommitCheckout checkout, String sha, boolean agentAttached, Path agentJar) {
        Path workDir = checkout.checkoutCommit(sha);
        Path depsFile = null;
        if (agentAttached) {
            try {
                depsFile = Files.createTempFile("blastradius-commit-deps-", ".json");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        GroundTruthResolution resolution = groundTruthResolver.resolve(
                workDir, agentAttached ? agentJar : null, depsFile);
        if (buildFailureDetector.isBuildFailure(resolution.initialBuild(), workDir)) {
            return CommitBuild.failed("commit " + sha + " failed to build (exit "
                    + resolution.initialBuild().exitCode() + "):\n"
                    + tail(resolution.initialBuild().output(), 4000));
        }
        DependencyRecordSet recordSet = agentAttached ? new DependencyRecordReader().readAll(depsFile) : null;
        return CommitBuild.succeeded(recordSet, resolution.results());
    }

    /**
     * The last {@code maxChars} of {@code text} (whole thing if shorter), for surfacing why a
     * build failed without dumping the entire reactor log into the failure reason. Maven prints
     * the real cause — a compile error, an unresolved dependency, a plugin that can't launch —
     * at the very end, so the tail is where the signal is.
     */
    private static String tail(String text, int maxChars) {
        if (text == null || text.isEmpty()) {
            return "(no build output captured)";
        }
        return text.length() <= maxChars ? text : "..." + text.substring(text.length() - maxChars);
    }

    /**
     * Builds the reactor scope from an already-materialized head tree. Best-effort: if the tree
     * can't be parsed into a graph (a malformed or unusual POM in some historical commit),
     * returns {@code null} so selection falls back to the safe whole-suite behavior rather than
     * aborting the pair — never a narrower scope on uncertainty (Constitution §III). The caller
     * owns getting {@code headTree} onto the head commit; both tree walks here skip {@code
     * target/}, so a populated build output on that tree costs nothing extra.
     */
    private ReactorScope buildReactorScope(Path headTree) {
        try {
            ReactorModuleGraph graph = reactorModuleGraphBuilder.fromRepoTree(headTree);
            return new ReactorScope(graph, TestModuleIndex.fromRepoTree(headTree, graph));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static PairAnalysis excluded(CommitPair pair, String reason) {
        CommitPair excludedPair = CommitPair.excluded(pair.baseCommit(), pair.headCommit(), reason);
        return new PairAnalysis(excludedPair, List.of(), FailureCoverage.empty(), List.of(), List.of());
    }

    /**
     * The build cache lives at {@code <report>.blastradius-build-cache/} — derived from the
     * report path, not a temp dir, so it survives a crashed run and lets the next invocation with
     * the same {@code --report-out} resume. Deriving it from the report (rather than a fixed
     * location) also keeps two runs writing to different reports from sharing — and possibly
     * cross-contaminating — a cache.
     */
    private static Path buildCacheDirectory(Path reportOutputPath) {
        Path report = reportOutputPath.toAbsolutePath();
        Path parent = report.getParent();
        String name = report.getFileName().toString() + ".blastradius-build-cache";
        return parent == null ? Path.of(name) : parent.resolve(name);
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
