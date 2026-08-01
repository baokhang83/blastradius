package io.github.baokhang83.blastradius.validator.mutation;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.ChangedFileClassifier;
import io.github.baokhang83.blastradius.core.reactor.ReactorModuleGraph;
import io.github.baokhang83.blastradius.core.reactor.ReactorModuleGraphBuilder;
import io.github.baokhang83.blastradius.core.reactor.ReactorScope;
import io.github.baokhang83.blastradius.core.reactor.TestModuleIndex;
import io.github.baokhang83.blastradius.core.selection.FallbackSelector;
import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.tracking.DependencyRecordReader;
import io.github.baokhang83.blastradius.core.tracking.DependencyRecordSet;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.validator.build.BuildFailureDetector;
import io.github.baokhang83.blastradius.validator.build.GroundTruthResolution;
import io.github.baokhang83.blastradius.validator.build.GroundTruthResolver;
import io.github.baokhang83.blastradius.validator.build.GroundTruthResult;
import io.github.baokhang83.blastradius.validator.build.JdkMismatchDetector;
import io.github.baokhang83.blastradius.validator.build.MavenBuildRunner;
import io.github.baokhang83.blastradius.validator.build.Outcome;
import io.github.baokhang83.blastradius.validator.git.CommitPair;
import io.github.baokhang83.blastradius.validator.report.ReportWriter;
import io.github.baokhang83.blastradius.validator.selection.PairSelectionAnalyzer;
import io.github.baokhang83.blastradius.validator.selection.PairSelectionResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.api.Git;

/** Runs bounded, opt-in mutation soundness validation against an isolated target clone. */
public final class MutationCommand {

    private final MutationCandidateGenerator candidateGenerator = new MutationCandidateGenerator();
    private final ChangedFileClassifier changedFileClassifier = new ChangedFileClassifier();
    private final FallbackSelector fallbackSelector = new FallbackSelector();
    private final ReactorModuleGraphBuilder reactorModuleGraphBuilder = new ReactorModuleGraphBuilder();
    private final PairSelectionAnalyzer pairSelectionAnalyzer = new PairSelectionAnalyzer();
    private final BuildFailureDetector buildFailureDetector = new BuildFailureDetector();
    private final ReportWriter reportWriter = new ReportWriter();

    public int run(MutationConfig config) {
        return run(config, locateOwnJar());
    }

    public int run(MutationConfig config, Path agentJar) {
        Path scratchParent = null;
        Path dependencyOutput = null;
        try {
            new JdkMismatchDetector().detect(config.projectPath()).ifPresent(System.err::println);
            scratchParent = Files.createTempDirectory("blastradius-mutations-");
            MavenBuildRunner buildRunner = new MavenBuildRunner(
                    config.mavenParallelThreads(), config.buildTimeoutMinutes(), false, config.skipBuildExtras());
            GroundTruthResolver groundTruthResolver = new GroundTruthResolver(buildRunner);
            try (SyntheticMutationCheckout checkout =
                    SyntheticMutationCheckout.forTargetProject(config.projectPath(), scratchParent)) {
                String baselineSha = head(checkout.workTree());
                Path baselineTree = checkout.checkoutBaseline(baselineSha);
                List<MutationCandidate> candidates = candidateGenerator.generate(
                        baselineTree, config.classFilter(), config.maxMutationClasses(), config.maxMutations());
                dependencyOutput = Files.createTempFile("blastradius-mutation-deps-", ".json");
                GroundTruthResolution baselineResolution =
                        groundTruthResolver.resolve(baselineTree, agentJar, dependencyOutput);
                if (buildFailureDetector.isBuildFailure(baselineResolution.initialBuild(), baselineTree)) {
                    System.err.println("blastradius-validator: baseline " + baselineSha + " failed to build");
                    return 2;
                }
                DependencyRecordSet baselineDependencies = new DependencyRecordReader().readAll(dependencyOutput);
                List<GroundTruthResult> baselineOutcomes = baselineResolution.results();
                List<TestIdentity> baselineFailingTests = baselineOutcomes.stream()
                        .filter(result -> result.outcome() == Outcome.CONFIRMED_FAILED)
                        .map(GroundTruthResult::test)
                        .toList();
                long deadline = System.nanoTime() + Duration.ofMinutes(config.timeLimitMinutes()).toNanos();
                List<MutationExperiment> experiments = new ArrayList<>();
                int timeLimitSkipped = 0;
                for (int index = 0; index < candidates.size(); index++) {
                    if (System.nanoTime() >= deadline) {
                        timeLimitSkipped = candidates.size() - index;
                        break;
                    }
                    MutationCandidate candidate = candidates.get(index);
                    checkout.checkoutBaseline(baselineSha);
                    String mutantSha = checkout.commit(candidate);
                    GroundTruthResolution mutantResolution = groundTruthResolver.resolve(checkout.workTree(), null, null);
                    if (buildFailureDetector.isBuildFailure(mutantResolution.initialBuild(), checkout.workTree())) {
                        experiments.add(new MutationExperiment(candidate, mutantSha, MutationStatus.UNBUILDABLE,
                                "mutant failed to build (exit " + mutantResolution.initialBuild().exitCode() + ")",
                                List.of(), List.of(), List.of(), List.of()));
                        continue;
                    }
                    experiments.add(analyzeMutant(candidate, baselineSha, mutantSha, checkout.workTree(),
                            baselineDependencies, baselineOutcomes, mutantResolution.results()));
                }
                MutationReport report = MutationReport.from(
                        baselineSha, baselineFailingTests, experiments, candidates.size(), timeLimitSkipped);
                reportWriter.write(config.reportOutputPath(), report);
                return report.verdict() == io.github.baokhang83.blastradius.validator.verdict.Verdict.PASS ? 0 : 1;
            }
        } catch (Exception e) {
            System.err.println("blastradius-validator: " + e.getMessage());
            return 2;
        } finally {
            deleteFile(dependencyOutput);
            deleteFile(scratchParent);
        }
    }

    private MutationExperiment analyzeMutant(
            MutationCandidate candidate,
            String baselineSha,
            String mutantSha,
            Path mutantTree,
            DependencyRecordSet baselineDependencies,
            List<GroundTruthResult> baselineOutcomes,
            List<GroundTruthResult> mutantOutcomes) {
        List<ChangedFile> changedFiles = changedFileClassifier.classify(mutantTree, baselineSha, mutantSha);
        ReactorScope scope = fallbackSelector.shouldFallback(changedFiles) ? buildReactorScope(mutantTree) : null;
        CommitPair edge = CommitPair.analyzed(baselineSha, mutantSha, changedFiles);
        PairSelectionResult selection = pairSelectionAnalyzer.analyze(
                edge, baselineDependencies, baselineOutcomes, mutantOutcomes, scope);
        Map<TestIdentity, Outcome> baselineByTest = new HashMap<>();
        baselineOutcomes.forEach(result -> baselineByTest.put(result.test(), result.outcome()));
        Map<TestIdentity, SelectionDecision> decisionByTest = new HashMap<>();
        selection.decisions().forEach(decision -> decisionByTest.put(decision.test(), decision));
        List<TestIdentity> killing = new ArrayList<>();
        List<TestIdentity> selected = new ArrayList<>();
        List<TestIdentity> skipped = new ArrayList<>();
        for (GroundTruthResult result : mutantOutcomes) {
            if (result.outcome() != Outcome.CONFIRMED_FAILED || baselineByTest.get(result.test()) != Outcome.PASSED) {
                continue;
            }
            killing.add(result.test());
            SelectionDecision decision = decisionByTest.get(result.test());
            if (decision != null && decision.selected()) {
                selected.add(result.test());
            } else {
                skipped.add(result.test());
            }
        }
        List<TestIdentity> flaky = selection.flakyFailures().stream().map(flakyFailure -> flakyFailure.test()).toList();
        return new MutationExperiment(candidate, mutantSha,
                killing.isEmpty() ? MutationStatus.SURVIVED : MutationStatus.KILLED,
                null, killing, selected, skipped, flaky);
    }

    private ReactorScope buildReactorScope(Path tree) {
        try {
            ReactorModuleGraph graph = reactorModuleGraphBuilder.fromRepoTree(tree);
            return new ReactorScope(graph, TestModuleIndex.fromRepoTree(tree, graph));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String head(Path repository) {
        try (Git git = Git.open(repository.toFile())) {
            return git.getRepository().resolve("HEAD").name();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to resolve target HEAD", e);
        }
    }

    private static void deleteFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The cloned checkout owns all material state; this only removes its empty parent or a temp record.
        }
    }

    private static Path locateOwnJar() {
        try {
            return Path.of(MutationCommand.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            throw new IllegalStateException("could not locate this tool's own jar for -javaagent attachment", e);
        }
    }
}
