package io.github.baokhang83.blastradius.validator.mutation;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.ChangedFileClassifier;
import io.github.baokhang83.blastradius.core.git.FileKind;
import io.github.baokhang83.blastradius.core.reactor.ModuleId;
import io.github.baokhang83.blastradius.core.reactor.ReactorModuleGraph;
import io.github.baokhang83.blastradius.core.reactor.ReactorModuleGraphBuilder;
import io.github.baokhang83.blastradius.core.reactor.ReactorScope;
import io.github.baokhang83.blastradius.core.reactor.TestModuleIndex;
import io.github.baokhang83.blastradius.core.selection.FallbackSelector;
import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.validator.build.BuildFailureDetector;
import io.github.baokhang83.blastradius.validator.build.CommitBuild;
import io.github.baokhang83.blastradius.validator.build.GroundTruthResolution;
import io.github.baokhang83.blastradius.validator.build.GroundTruthResolver;
import io.github.baokhang83.blastradius.validator.build.GroundTruthResult;
import io.github.baokhang83.blastradius.validator.build.Outcome;
import io.github.baokhang83.blastradius.validator.git.CommitCheckout;
import io.github.baokhang83.blastradius.validator.git.CommitPair;
import io.github.baokhang83.blastradius.validator.selection.PairSelectionAnalyzer;
import io.github.baokhang83.blastradius.validator.selection.PairSelectionResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Runs bounded synthetic mutants as evidence for one already-built historical pair. */
public final class HistoricalMutationValidator {

    /** Only production sources are mutable; a changed test or Kotlin file carries no candidate. */
    private static final String PRODUCTION_SOURCE_MARKER = "src/main/java/";

    private final MutationCandidateGenerator candidateGenerator = new MutationCandidateGenerator();
    private final ChangedFileClassifier changedFileClassifier = new ChangedFileClassifier();
    private final FallbackSelector fallbackSelector = new FallbackSelector();
    private final ReactorModuleGraphBuilder reactorModuleGraphBuilder = new ReactorModuleGraphBuilder();
    private final PairSelectionAnalyzer pairSelectionAnalyzer = new PairSelectionAnalyzer();
    private final BuildFailureDetector buildFailureDetector = new BuildFailureDetector();
    private final GroundTruthResolver groundTruthResolver;

    public HistoricalMutationValidator(GroundTruthResolver groundTruthResolver) {
        this.groundTruthResolver = groundTruthResolver;
    }

    public PairMutationResult validate(
            CommitPair pair, CommitBuild base, CommitBuild head, CommitCheckout checkout,
            MutationValidationConfig config, long deadlineNanos) {
        Path headTree = checkout.checkoutCommit(pair.headCommit());
        // Mutate the code THIS pair changed, not the whole-tree first-yielding class: a real
        // regression lives in the changed surface, and the changed module's -amd fanout is
        // typically cheaper to build than a low-level module's. ChangedFile.path is repo-relative,
        // the same form MutationCandidate.sourcePath uses, so the pool is a direct set-membership.
        Set<String> changedSourcePaths = changedFileClassifier.classify(
                        headTree, pair.baseCommit(), pair.headCommit()).stream()
                .filter(changed -> changed.kind() == FileKind.JAVA_SOURCE)
                .map(ChangedFile::path)
                .filter(path -> path.contains(PRODUCTION_SOURCE_MARKER))
                .collect(Collectors.toSet());
        List<MutationCandidate> candidates = candidateGenerator.generate(headTree, config.classFilter(),
                changedSourcePaths, config.maxMutationClassesPerPair(), config.maxMutationsPerPair());
        if (candidates.isEmpty()) {
            // The pair changed no mutable production source (docs/config/test-only, or a changed
            // source with no boolean/operator token). Fall back to the whole-tree scan so the pair
            // still exercises some mutant rather than silently validating nothing (§III).
            candidates = candidateGenerator.generate(headTree, config.classFilter(),
                    config.maxMutationClassesPerPair(), config.maxMutationsPerPair());
        }
        // One reactor graph for the whole pair: each mutant changes a single file, so its build
        // only needs the owning module + its dependents (-pl/-am/-amd), not the whole reactor.
        ReactorModuleGraph moduleGraph = buildModuleGraph(headTree);
        List<TestIdentity> headFailures = head.groundTruth().stream()
                .filter(result -> result.outcome() == Outcome.CONFIRMED_FAILED)
                .map(GroundTruthResult::test)
                .toList();
        Map<TestIdentity, Outcome> headOutcomes = new HashMap<>();
        head.groundTruth().forEach(result -> headOutcomes.put(result.test(), result.outcome()));
        List<MutationExperiment> experiments = new ArrayList<>();
        int timeSkipped = 0;
        for (int index = 0; index < candidates.size(); index++) {
            if (System.nanoTime() >= deadlineNanos) {
                timeSkipped = candidates.size() - index;
                break;
            }
            MutationCandidate candidate = candidates.get(index);
            checkout.checkoutCommit(pair.headCommit());
            String mutantCommit = commitMutation(checkout, candidate);
            String modulePath = modulePathFor(moduleGraph, candidate.sourcePath());
            GroundTruthResolution mutantResolution =
                    groundTruthResolver.resolve(checkout.workTree(), null, null, modulePath);
            if (buildFailureDetector.isBuildFailure(mutantResolution.initialBuild(), checkout.workTree())) {
                experiments.add(new MutationExperiment(pair, candidate, mutantCommit, MutationStatus.UNBUILDABLE,
                        "mutant failed to build (exit " + mutantResolution.initialBuild().exitCode() + ")",
                        headFailures, List.of(), List.of(), List.of(), List.of()));
                continue;
            }
            experiments.add(analyzeMutant(pair, candidate, mutantCommit, base, headFailures, headOutcomes,
                    mutantResolution.results(), checkout.workTree()));
        }
        return new PairMutationResult(experiments, candidates.size(), timeSkipped);
    }

    private MutationExperiment analyzeMutant(
            CommitPair pair, MutationCandidate candidate, String mutantCommit, CommitBuild base,
            List<TestIdentity> headFailures, Map<TestIdentity, Outcome> headOutcomes,
            List<GroundTruthResult> mutantOutcomes, Path mutantTree) {
        List<ChangedFile> changedFiles = changedFileClassifier.classify(mutantTree, pair.baseCommit(), mutantCommit);
        ReactorScope scope = fallbackSelector.shouldFallback(changedFiles) ? buildReactorScope(mutantTree) : null;
        CommitPair selectionEdge = CommitPair.analyzed(pair.baseCommit(), mutantCommit, changedFiles);
        PairSelectionResult selection = pairSelectionAnalyzer.analyze(
                selectionEdge, base.dependencyRecordSet(), base.groundTruth(), mutantOutcomes, scope);
        Map<TestIdentity, SelectionDecision> decisions = new HashMap<>();
        selection.decisions().forEach(decision -> decisions.put(decision.test(), decision));
        List<TestIdentity> killing = new ArrayList<>();
        List<TestIdentity> selected = new ArrayList<>();
        List<TestIdentity> skipped = new ArrayList<>();
        for (GroundTruthResult outcome : mutantOutcomes) {
            if (outcome.outcome() != Outcome.CONFIRMED_FAILED
                    || headOutcomes.getOrDefault(outcome.test(), Outcome.PASSED) != Outcome.PASSED) {
                continue;
            }
            killing.add(outcome.test());
            SelectionDecision decision = decisions.get(outcome.test());
            if (decision != null && decision.selected()) {
                selected.add(outcome.test());
            } else {
                skipped.add(outcome.test());
            }
        }
        List<TestIdentity> flaky = selection.flakyFailures().stream().map(failure -> failure.test()).toList();
        return new MutationExperiment(pair, candidate, mutantCommit,
                killing.isEmpty() ? MutationStatus.SURVIVED : MutationStatus.KILLED,
                null, headFailures, killing, selected, skipped, flaky);
    }

    private ReactorModuleGraph buildModuleGraph(Path tree) {
        try {
            return reactorModuleGraphBuilder.fromRepoTree(tree);
        } catch (RuntimeException e) {
            // A graph we can't build means we can't safely narrow scope — fall back to whole-reactor
            // builds (modulePathFor returns null for every candidate), never guess narrower (§III).
            return null;
        }
    }

    /**
     * The {@code -pl} module directory for the module owning {@code sourcePath}, or {@code null}
     * to build the whole reactor when the graph is unavailable, the path maps to no single module,
     * or the owning module is the reactor root itself (where {@code -pl} would be redundant).
     */
    private static String modulePathFor(ReactorModuleGraph moduleGraph, String sourcePath) {
        if (moduleGraph == null) {
            return null;
        }
        return moduleGraph.moduleOf(sourcePath)
                .map(ModuleId::relativePath)
                .filter(relativePath -> !relativePath.isEmpty())
                .orElse(null);
    }

    private ReactorScope buildReactorScope(Path tree) {
        try {
            ReactorModuleGraph graph = reactorModuleGraphBuilder.fromRepoTree(tree);
            return new ReactorScope(graph, TestModuleIndex.fromRepoTree(tree, graph));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String commitMutation(CommitCheckout checkout, MutationCandidate candidate) {
        Path source = checkout.workTree().resolve(candidate.sourcePath());
        try {
            return checkout.commitFile(Path.of(candidate.sourcePath()), candidate.applyTo(Files.readString(source)),
                    "blastradius mutation: " + candidate.className() + " " + candidate.operator());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to mutate " + candidate.sourcePath(), e);
        }
    }

    public record PairMutationResult(List<MutationExperiment> experiments, int generated, int timeLimitSkipped) {}
}
