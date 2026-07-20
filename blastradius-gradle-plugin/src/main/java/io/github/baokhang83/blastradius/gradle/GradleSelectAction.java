package io.github.baokhang83.blastradius.gradle;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.FileKind;
import io.github.baokhang83.blastradius.core.selection.NewOrModifiedTestSelector;
import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.selection.SelectionEngine;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.tasks.testing.Test;

final class GradleSelectAction {

    private static final String NO_SELECTION_PATTERN = "__blastradius__.NoSelectedTests";

    private final Project project;
    private final BlastradiusExtension extension;
    private final CurrentChangesResolver currentChangesResolver = new CurrentChangesResolver();
    private final IndexApplicabilityResolver indexApplicabilityResolver = new IndexApplicabilityResolver();
    private final TestDiscoverer testDiscoverer = new TestDiscoverer();
    GradleSelectAction(Project project, BlastradiusExtension extension) {
        this.project = project;
        this.extension = extension;
    }

    void apply(Test test) {
        Path projectRoot = project.getRootDir().toPath();
        Path indexPath = resolveIndexPath(projectRoot);
        CurrentChanges changes = currentChangesResolver.resolve(projectRoot, extension.getBaseRef().get());
        IndexApplicability applicability = indexApplicabilityResolver.resolve(indexPath, projectRoot);
        if (changes.baseRefBuild() || applicability.status() != IndexApplicability.Status.APPLICABLE) {
            project.getLogger().info("[blastradius] Gradle test task left unfiltered ({})",
                    changes.baseRefBuild() ? "TRACK not yet implemented" : applicability.status());
            return;
        }

        try {
            Set<TestIdentity> allTests = testDiscoverer.discoverAllTests(test);
            List<SelectionDecision> decisions = computeDecisions(allTests, applicability.index(), changes.changedFiles());
            applyFilter(test, decisions);
            project.getLogger().lifecycle("[blastradius] SELECT — {} / {} tests selected", decisions.stream()
                    .filter(SelectionDecision::selected)
                    .count(), decisions.size());
        } catch (RuntimeException e) {
            project.getLogger().warn("[blastradius] selection failed; running the full test task", e);
        }
    }

    private Path resolveIndexPath(Path projectRoot) {
        Path normalizedRoot = projectRoot.normalize();
        Path resolved = projectRoot.resolve(extension.getIndexPath().get()).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new GradleException("indexPath resolves outside the root project: " + resolved);
        }
        return resolved;
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
                .filter(file -> file.kind() == FileKind.JAVA_SOURCE)
                .map(ChangedFile::changedClassName)
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
