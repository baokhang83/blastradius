package io.github.baokhang83.blastradius.gradle;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.ChangedFileClassifier;
import io.github.baokhang83.blastradius.core.index.CommitIndexKey;
import io.github.baokhang83.blastradius.core.index.FileIndexStore;
import io.github.baokhang83.blastradius.core.index.IndexStore;
import io.github.baokhang83.blastradius.core.selection.NewOrModifiedTestSelector;
import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.selection.SelectionEngine;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.tasks.testing.Test;

/** Applies the dependency-based filter using only configuration-time values and the {@link Test} task. */
final class ApplySelectionAction implements Action<Task> {

    private static final String NO_SELECTION_PATTERN = "__blastradius__.NoSelectedTests";

    private final File repositoryDirectory;
    private final String indexPathKey;
    private final String comparisonBaseCommit;
    private final String headCommit;
    private final ConfiguredIndexStore configuredStore;
    private final boolean directInvocationFallback;

    ApplySelectionAction(File repositoryDirectory, String indexPathKey, String comparisonBaseCommit, String headCommit,
            ConfiguredIndexStore configuredStore, boolean directInvocationFallback) {
        this.repositoryDirectory = repositoryDirectory;
        this.indexPathKey = indexPathKey;
        this.comparisonBaseCommit = comparisonBaseCommit;
        this.headCommit = headCommit;
        this.configuredStore = configuredStore;
        this.directInvocationFallback = directInvocationFallback;
    }

    @Override
    public void execute(Task task) {
        Test test = (Test) task;
        Path repositoryRoot = repositoryDirectory.toPath().toAbsolutePath().normalize();
        String indexKey = CommitIndexKey.forCommit(indexPathKey, comparisonBaseCommit);
        IndexStore<DependencyIndex> indexStore = configuredStore.create(repositoryDirectory);
        IndexApplicability applicability;
        try {
            applicability = new IndexApplicabilityResolver().resolve(indexStore, indexKey, comparisonBaseCommit, repositoryRoot);
        } finally {
            ConfiguredIndexStore.close(indexStore);
        }
        if (applicability.status() != IndexApplicability.Status.APPLICABLE) {
            if (applicability.status() == IndexApplicability.Status.FORMAT_VERSION_MISMATCH) {
                test.getLogger().lifecycle(
                        "[blastradius] FALLBACK — persisted index uses an unsupported format version (FORMAT_VERSION_MISMATCH)");
                return;
            }
            test.getLogger().info("[blastradius] Gradle test task left unfiltered ({})", applicability.status());
            return;
        }

        try {
            Set<TestIdentity> allTests = new TestDiscoverer().discoverAllTests(
                    test.getClasspath().getFiles(), test.getTestClassesDirs().getFiles());
            List<ChangedFile> changedFiles = new ChangedFileClassifier().classify(
                    repositoryDirectory.toPath(), comparisonBaseCommit, headCommit);
            List<SelectionDecision> decisions = computeDecisions(
                    allTests, applicability.index(), changedFiles, directInvocationFallback);
            applyFilter(test, decisions);
            test.getLogger().lifecycle("[blastradius] SELECT — {} / {} tests selected", decisions.stream()
                    .filter(SelectionDecision::selected)
                    .count(), decisions.size());
        } catch (RuntimeException e) {
            test.getLogger().warn("[blastradius] selection failed; running the full test task", e);
        }
    }

    private static List<SelectionDecision> computeDecisions(Set<TestIdentity> allTests, DependencyIndex index,
            List<ChangedFile> changedFiles) {
        return computeDecisions(allTests, index, changedFiles, true);
    }

    private static List<SelectionDecision> computeDecisions(Set<TestIdentity> allTests, DependencyIndex index,
            List<ChangedFile> changedFiles, boolean directInvocationFallback) {
        Map<TestIdentity, Set<String>> dependenciesByTest = index.testDependenciesByTest().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().baselineKey(),
                        Map.Entry::getValue,
                        (first, second) -> {
                            Set<String> merged = new java.util.HashSet<>(first);
                            merged.addAll(second);
                            return merged;
                        }));
        Map<TestIdentity, Map<String, Set<String>>> directInvocationsByTest = index.directInvocationsByTest()
                .entrySet().stream().collect(Collectors.toMap(
                        entry -> entry.getKey().baselineKey(),
                        Map.Entry::getValue,
                        ApplySelectionAction::mergeDirectInvocations));
        Set<String> changedClassNames = changedFiles.stream()
                .flatMap(file -> file.candidateClassNames().stream())
                .collect(Collectors.toUnmodifiableSet());
        Set<TestIdentity> newOrModifiedTests = allTests.stream()
                .filter(test -> new NewOrModifiedTestSelector().appliesTo(
                        test, !dependenciesByTest.containsKey(test.baselineKey()), changedClassNames))
                .collect(Collectors.toSet());
        return new SelectionEngine().selectAll(
                allTests, dependenciesByTest, directInvocationFallback ? directInvocationsByTest : Map.of(), newOrModifiedTests,
                changedFiles, index.ambientDependencies());
    }

    private static Map<String, Set<String>> mergeDirectInvocations(
            Map<String, Set<String>> first, Map<String, Set<String>> second) {
        Map<String, Set<String>> merged = new java.util.HashMap<>(first);
        second.forEach((source, targets) -> merged.merge(source, targets, (left, right) -> {
            Set<String> union = new java.util.HashSet<>(left);
            union.addAll(right);
            return Set.copyOf(union);
        }));
        return Map.copyOf(merged);
    }

    private static void applyFilter(Test test, List<SelectionDecision> decisions) {
        List<TestIdentity> selectedTests = decisions.stream()
                .filter(SelectionDecision::selected)
                .map(SelectionDecision::test)
                .toList();
        if (selectedTests.isEmpty()) {
            test.getFilter().includeTestsMatching(NO_SELECTION_PATTERN);
            test.getFilter().setFailOnNoMatchingTests(false);
            return;
        }
        selectedTests.forEach(selected -> test.getFilter().includeTest(selected.className(), selected.methodName()));
    }
}
