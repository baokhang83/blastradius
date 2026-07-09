package io.github.baokhang83.blastradius.plugin.mojo;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.FileKind;
import io.github.baokhang83.blastradius.core.selection.NewOrModifiedTestSelector;
import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.selection.SelectionEngine;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.plugin.diff.CurrentChanges;
import io.github.baokhang83.blastradius.plugin.diff.CurrentChangesResolver;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.IndexApplicability;
import io.github.baokhang83.blastradius.plugin.index.IndexApplicabilityResolver;
import io.github.baokhang83.blastradius.plugin.report.BuildReport;
import io.github.baokhang83.blastradius.plugin.report.BuildReportWriter;
import io.github.baokhang83.blastradius.plugin.report.ConsoleSummaryRenderer;
import io.github.baokhang83.blastradius.plugin.report.ExplainListingRenderer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * The {@code blastradius:select} goal — computes dependency-based test selection for the
 * current build and narrows Surefire/Failsafe to it, or refreshes the persisted dependency
 * index, depending on which of three modes applies (research.md #1; contracts/
 * mojo-and-index-contract.md).
 *
 * <p>{@code SELECT} (US1/US2/US3) computes and applies a narrowed Surefire filter, and
 * writes/renders a {@link BuildReport} of its decisions. {@code TRACK}/{@code FALLBACK}
 * (US4) are filled in later.
 */
@Mojo(name = "select", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES,
        requiresDependencyResolution = ResolutionScope.TEST)
public final class SelectMojo extends AbstractMojo {

    @Parameter(property = "baseRef", required = true)
    private String baseRef;

    @Parameter(property = "indexPath", defaultValue = ".blastradius/index.json")
    private String indexPath;

    @Parameter(property = "blastradius.mode")
    private String mode;

    @Parameter(property = "blastradius.explain", defaultValue = "false")
    private boolean explain;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    private static final SelectionEngine SELECTION_ENGINE = new SelectionEngine();
    private static final NewOrModifiedTestSelector NEW_OR_MODIFIED_TEST_SELECTOR = new NewOrModifiedTestSelector();

    private final CurrentChangesResolver currentChangesResolver = new CurrentChangesResolver();
    private final IndexApplicabilityResolver indexApplicabilityResolver = new IndexApplicabilityResolver();
    private final SurefireFilterApplier surefireFilterApplier = new SurefireFilterApplier();
    private final TestDiscoverer testDiscoverer = new TestDiscoverer();
    private final BuildReportWriter buildReportWriter = new BuildReportWriter();
    private final ConsoleSummaryRenderer consoleSummaryRenderer = new ConsoleSummaryRenderer();
    private final ExplainListingRenderer explainListingRenderer = new ExplainListingRenderer();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // Anchored at the reactor root (not project.getBasedir()), which for a
        // multi-module reactor differs per module: the git repository only exists at the
        // root (JGit's Git.open does not search upward for it), and the persisted index
        // is one shared file the whole reactor's tracking agent already populated
        // module-agnostically (README's cross-module attribution) — every module's own
        // goal execution must resolve that same file, not one relative to its own
        // basedir (tasks.md T026/T029, FR-010).
        Path reactorRoot = Path.of(session.getExecutionRootDirectory());
        Path resolvedIndexPath = reactorRoot.resolve(indexPath);

        CurrentChanges changes = currentChangesResolver.resolve(reactorRoot, baseRef);
        IndexApplicability applicability = indexApplicabilityResolver.resolve(resolvedIndexPath, reactorRoot);

        BuildReport.Mode resolvedMode = determineMode(changes, applicability, mode);

        switch (resolvedMode) {
            case TRACK -> runTrack();
            case SELECT -> runSelect(changes, applicability);
            case FALLBACK -> runFallback();
        }
    }

    /**
     * The mode-routing decision itself (research.md #1; contracts' three-state table) —
     * a plain static method, not an instance method, so it's directly testable without a
     * Mojo-testing harness: a base-ref build (or an explicit {@code -Dblastradius.mode=track})
     * always tracks; otherwise a valid index selects, and anything else falls back.
     */
    static BuildReport.Mode determineMode(CurrentChanges changes, IndexApplicability applicability, String explicitMode) {
        if (changes.isBaseRefBuild() || "track".equalsIgnoreCase(explicitMode)) {
            return BuildReport.Mode.TRACK;
        }
        if (applicability.status() == IndexApplicability.Status.APPLICABLE) {
            return BuildReport.Mode.SELECT;
        }
        return BuildReport.Mode.FALLBACK;
    }

    private void runTrack() {
        // Filled in by User Story 4 (tasks.md T045).
    }

    private void runSelect(CurrentChanges changes, IndexApplicability applicability) throws MojoExecutionException {
        Path testClassesDir = Path.of(project.getBuild().getTestOutputDirectory());
        List<String> testClasspathElements;
        try {
            testClasspathElements = project.getTestClasspathElements();
        } catch (org.apache.maven.artifact.DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("failed to resolve the test classpath for " + project.getArtifactId(), e);
        }

        Set<TestIdentity> allTests = testDiscoverer.discoverAllTests(testClassesDir, testClasspathElements);
        List<SelectionDecision> decisions = computeDecisions(allTests, applicability.index(), changes.changedFiles());
        Set<TestIdentity> selectedTests = decisions.stream()
                .filter(SelectionDecision::selected)
                .map(SelectionDecision::test)
                .collect(Collectors.toSet());

        surefireFilterApplier.apply(project, selectedTests);

        BuildReport report = BuildReport.forSelect(applicability, decisions);
        buildReportWriter.write(reportPath(), report);
        renderReport(report, applicability.index());
    }

    private void renderReport(BuildReport report, DependencyIndex usedIndex) {
        consoleSummaryRenderer.render(report, usedIndex).forEach(getLog()::info);
        if (explain) {
            explainListingRenderer.render(report).forEach(getLog()::info);
        }
    }

    private Path reportPath() {
        return project.getBasedir().toPath().resolve(".blastradius/last-build-report.json");
    }

    private void runFallback() {
        // Filled in by User Story 4 (tasks.md T046).
    }

    /**
     * Computes one {@link SelectionDecision} per test, reusing {@code blastradius-core}'s
     * proven {@link SelectionEngine} unchanged — no plugin-specific selection logic lives
     * here. {@link TestIdentity#baselineKey()} normalizes the lookup against the index (a
     * parameterized test's ground-truth identity and its tracked baseline identity can
     * differ — see the validator's own T061 finding this fix addresses).
     */
    static List<SelectionDecision> computeDecisions(Set<TestIdentity> allTests, DependencyIndex index,
            List<ChangedFile> changedFiles) {
        Map<TestIdentity, Set<String>> testDependencies = index.testDependenciesByTest().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().baselineKey(),
                        Map.Entry::getValue,
                        (a, b) -> {
                            Set<String> union = new java.util.HashSet<>(a);
                            union.addAll(b);
                            return union;
                        }));

        Set<String> changedClassNames = changedFiles.stream()
                .filter(f -> f.kind() == FileKind.JAVA_SOURCE)
                .map(ChangedFile::changedClassName)
                .collect(Collectors.toUnmodifiableSet());

        Set<TestIdentity> newOrModifiedTests = allTests.stream()
                .filter(test -> NEW_OR_MODIFIED_TEST_SELECTOR.appliesTo(
                        test, !testDependencies.containsKey(test.baselineKey()), changedClassNames))
                .collect(Collectors.toSet());

        return SELECTION_ENGINE.selectAll(allTests, testDependencies, newOrModifiedTests, changedFiles);
    }
}
