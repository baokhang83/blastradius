package io.github.baokhang83.blastradius.gradle;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.ChangedFileClassifier;
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
    private final File indexFile;
    private final String baseCommit;
    private final String headCommit;

    ApplySelectionAction(File repositoryDirectory, File indexFile, String baseCommit, String headCommit) {
        this.repositoryDirectory = repositoryDirectory;
        this.indexFile = indexFile;
        this.baseCommit = baseCommit;
        this.headCommit = headCommit;
    }

    @Override
    public void execute(Task task) {
        Test test = (Test) task;
        Path repositoryRoot = repositoryDirectory.toPath().toAbsolutePath().normalize();
        String indexKey = repositoryRoot.relativize(indexFile.toPath().toAbsolutePath().normalize()).toString();
        IndexStore<DependencyIndex> indexStore = new FileIndexStore<>(repositoryRoot, DependencyIndex.class);
        IndexApplicability applicability = new IndexApplicabilityResolver().resolve(indexStore, indexKey, repositoryRoot);
        if (applicability.status() != IndexApplicability.Status.APPLICABLE) {
            test.getLogger().info("[blastradius] Gradle test task left unfiltered ({})", applicability.status());
            return;
        }

        try {
            Set<TestIdentity> allTests = new TestDiscoverer().discoverAllTests(
                    test.getClasspath().getFiles(), test.getTestClassesDirs().getFiles());
            List<ChangedFile> changedFiles = new ChangedFileClassifier().classify(
                    repositoryDirectory.toPath(), baseCommit, headCommit);
            List<SelectionDecision> decisions = computeDecisions(allTests, applicability.index(), changedFiles);
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
        Map<TestIdentity, Set<String>> dependenciesByTest = index.testDependenciesByTest().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().baselineKey(),
                        Map.Entry::getValue,
                        (first, second) -> {
                            Set<String> merged = new java.util.HashSet<>(first);
                            merged.addAll(second);
                            return merged;
                        }));
        Set<String> changedClassNames = changedFiles.stream()
                .flatMap(file -> file.candidateClassNames().stream())
                .collect(Collectors.toUnmodifiableSet());
        Set<TestIdentity> newOrModifiedTests = allTests.stream()
                .filter(test -> new NewOrModifiedTestSelector().appliesTo(
                        test, !dependenciesByTest.containsKey(test.baselineKey()), changedClassNames))
                .collect(Collectors.toSet());
        return new SelectionEngine().selectAll(allTests, dependenciesByTest, newOrModifiedTests, changedFiles);
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
