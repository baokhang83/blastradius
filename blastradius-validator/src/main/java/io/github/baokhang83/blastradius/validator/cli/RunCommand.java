package io.github.baokhang83.blastradius.validator.cli;

import io.github.baokhang83.blastradius.validator.build.BuildFailureDetector;
import io.github.baokhang83.blastradius.validator.build.BuildResult;
import io.github.baokhang83.blastradius.validator.build.GroundTruthResolver;
import io.github.baokhang83.blastradius.validator.build.GroundTruthResult;
import io.github.baokhang83.blastradius.validator.build.MavenBuildRunner;
import io.github.baokhang83.blastradius.validator.build.Outcome;
import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.ChangedFileClassifier;
import io.github.baokhang83.blastradius.validator.git.CommitCheckout;
import io.github.baokhang83.blastradius.validator.git.CommitPair;
import io.github.baokhang83.blastradius.validator.git.CommitWindowResolver;
import io.github.baokhang83.blastradius.core.git.FileKind;
import io.github.baokhang83.blastradius.validator.git.PairStatus;
import io.github.baokhang83.blastradius.validator.report.AnalysisReport;
import io.github.baokhang83.blastradius.validator.report.ReportWriter;
import io.github.baokhang83.blastradius.validator.report.SavingsSummary;
import io.github.baokhang83.blastradius.validator.report.SavingsSummaryAggregator;
import io.github.baokhang83.blastradius.core.selection.NewOrModifiedTestSelector;
import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.selection.SelectionEngine;
import io.github.baokhang83.blastradius.core.tracking.DependencyRecordReader;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.validator.verdict.FlakyFailure;
import io.github.baokhang83.blastradius.validator.verdict.Verdict;
import io.github.baokhang83.blastradius.validator.verdict.VerdictCalculator;
import io.github.baokhang83.blastradius.validator.verdict.WouldMissCase;
import io.github.baokhang83.blastradius.validator.verdict.WouldMissComparator;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wires the full pipeline together: for each commit pair in the resolved window, checks
 * out the base commit to obtain a dependency baseline, checks out the head commit to
 * obtain ground truth, classifies changes, runs selection, and compares against ground
 * truth for would-miss cases — then computes the overall verdict and writes the report.
 * A pair whose base or head commit fails to build is excluded (FR-009) rather than
 * aborting the whole run.
 *
 * <p>Exit codes per the CLI contract: {@code 0} = PASS, {@code 1} = FAIL, {@code 2} =
 * the run itself could not complete.
 */
public final class RunCommand {

    private final CommitWindowResolver commitWindowResolver = new CommitWindowResolver();
    private final ChangedFileClassifier changedFileClassifier = new ChangedFileClassifier();
    private final GroundTruthResolver groundTruthResolver = new GroundTruthResolver();
    private final SelectionEngine selectionEngine = new SelectionEngine();
    private final NewOrModifiedTestSelector newOrModifiedTestSelector = new NewOrModifiedTestSelector();
    private final WouldMissComparator wouldMissComparator = new WouldMissComparator();
    private final VerdictCalculator verdictCalculator = new VerdictCalculator();
    private final SavingsSummaryAggregator savingsSummaryAggregator = new SavingsSummaryAggregator();
    private final ReportWriter reportWriter = new ReportWriter();
    private final MavenBuildRunner buildRunner = new MavenBuildRunner();
    private final BuildFailureDetector buildFailureDetector = new BuildFailureDetector();

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
            List<CommitPair> window =
                    commitWindowResolver.resolveWindow(config.projectPath(), config.commitWindowSize());

            List<CommitPair> analyzedPairs = new ArrayList<>();
            List<CommitPair> excludedPairs = new ArrayList<>();
            List<WouldMissCase> allMisses = new ArrayList<>();
            List<SelectionDecision> allDecisions = new ArrayList<>();
            List<FlakyFailure> allFlaky = new ArrayList<>();

            Path scratchParent = Files.createTempDirectory("blastradius-scratch-");
            try (CommitCheckout checkout = CommitCheckout.forTargetProject(config.projectPath(), scratchParent)) {
                for (CommitPair pair : window) {
                    PairAnalysis analysis = analyzePair(pair, config.projectPath(), checkout, agentJar);
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

    private PairAnalysis analyzePair(CommitPair pair, Path targetRepo, CommitCheckout checkout, Path agentJar)
            throws Exception {
        // Baseline: build the BASE commit with the agent attached, to learn what each
        // test depended on as of that commit.
        Path baseWorkDir = checkout.checkoutCommit(pair.baseCommit());
        Path baseDepsFile = Files.createTempFile("blastradius-base-deps-", ".json");
        BuildResult baseResult = buildRunner.run(baseWorkDir, agentJar, baseDepsFile);
        if (buildFailureDetector.isBuildFailure(baseResult, baseWorkDir)) {
            return excluded(pair, "base commit " + pair.baseCommit() + " failed to build");
        }
        Map<TestIdentity, Map<String, String>> baseRecord = new DependencyRecordReader().readAll(baseDepsFile);
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

        // Ground truth: check the HEAD commit builds before delegating to
        // GroundTruthResolver's own (separate) full run, since a compile failure there
        // would otherwise silently look like "zero tests" rather than EXCLUDED.
        Path headWorkDir = checkout.checkoutCommit(pair.headCommit());
        BuildResult headProbe = buildRunner.run(headWorkDir, null, null);
        if (buildFailureDetector.isBuildFailure(headProbe, headWorkDir)) {
            return excluded(pair, "head commit " + pair.headCommit() + " failed to build");
        }
        List<GroundTruthResult> groundTruth = groundTruthResolver.resolve(headWorkDir, null, null);

        // Changed files, classified, against the real target repo's git history.
        List<ChangedFile> changedFiles =
                changedFileClassifier.classify(targetRepo, pair.baseCommit(), pair.headCommit());
        Set<String> changedClassNames = changedFiles.stream()
                .filter(f -> f.kind() == FileKind.JAVA_SOURCE)
                .map(ChangedFile::changedClassName)
                .collect(Collectors.toUnmodifiableSet());

        Set<TestIdentity> allTests = groundTruth.stream().map(GroundTruthResult::test).collect(Collectors.toSet());
        Set<TestIdentity> newOrModifiedTests = allTests.stream()
                .filter(test -> newOrModifiedTestSelector.appliesTo(
                        test, !testDependencies.containsKey(test.baselineKey()), changedClassNames))
                .collect(Collectors.toSet());

        List<SelectionDecision> decisions =
                selectionEngine.selectAll(allTests, testDependencies, newOrModifiedTests, changedFiles);

        CommitPair enrichedPair = CommitPair.analyzed(pair.baseCommit(), pair.headCommit(), changedFiles);
        List<WouldMissCase> misses = wouldMissComparator.compare(enrichedPair, decisions, groundTruth);
        List<FlakyFailure> flakyFailures = groundTruth.stream()
                .filter(r -> r.outcome() == Outcome.FLAKY)
                .map(r -> new FlakyFailure(enrichedPair, r.test()))
                .toList();

        return new PairAnalysis(enrichedPair, misses, decisions, flakyFailures);
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
