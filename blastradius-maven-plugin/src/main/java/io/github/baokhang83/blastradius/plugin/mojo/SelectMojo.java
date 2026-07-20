package io.github.baokhang83.blastradius.plugin.mojo;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.selection.NewOrModifiedTestSelector;
import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.selection.SelectionEngine;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.plugin.diff.CurrentChanges;
import io.github.baokhang83.blastradius.plugin.diff.CurrentChangesResolver;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndexWriter;
import io.github.baokhang83.blastradius.plugin.index.IndexApplicability;
import io.github.baokhang83.blastradius.plugin.index.IndexApplicabilityResolver;
import io.github.baokhang83.blastradius.plugin.report.BuildReport;
import io.github.baokhang83.blastradius.plugin.report.BuildReportWriter;
import io.github.baokhang83.blastradius.plugin.report.ConsoleSummaryRenderer;
import io.github.baokhang83.blastradius.plugin.report.ExplainListingRenderer;
import io.github.baokhang83.blastradius.plugin.track.TrackRunner;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
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
 * <p>{@code SELECT} (US1/US2/US3) computes and applies a narrowed Surefire filter.
 * {@code TRACK} (US4) forks a subprocess to refresh the index while running this build's
 * own suite unfiltered; {@code FALLBACK} (US4) also runs unfiltered but forks nothing.
 * Every mode writes/renders a {@link BuildReport} of its outcome.
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

    /**
     * Set only by {@link TrackRunner}, on the {@code mvn} subprocess it forks against this
     * same project directory to gather instrumented dependency data. That subprocess's pom
     * is the adopting project's own pom — the same one this goal is bound in — so without
     * this guard the subprocess's own {@code execute()} would resolve {@code TRACK} again
     * (same commit, same {@code baseRef}) and fork another subprocess, recursing without
     * bound. The tracking data comes entirely from the {@code -javaagent} attached via
     * {@code JAVA_TOOL_OPTIONS}, not from this goal, so skipping is safe and correct.
     */
    @Parameter(property = "blastradius.trackChild", defaultValue = "false")
    private boolean trackChild;

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
    private final TrackRunner trackRunner = new TrackRunner();
    private final DependencyIndexWriter dependencyIndexWriter = new DependencyIndexWriter();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (trackChild) {
            getLog().info("[blastradius] skipping select goal (this build is a TRACK-mode subprocess "
                    + "collecting dependency data; the ambient gated build's own Surefire execution is untouched)");
            return;
        }

        // Anchored at the reactor root (not project.getBasedir()), which for a
        // multi-module reactor differs per module: the git repository only exists at the
        // root (JGit's Git.open does not search upward for it), and the persisted index
        // is one shared file the whole reactor's tracking agent already populated
        // module-agnostically (README's cross-module attribution) — every module's own
        // goal execution must resolve that same file, not one relative to its own
        // basedir (tasks.md T026/T029, FR-010).
        Path reactorRoot = Path.of(session.getExecutionRootDirectory());
        Path normalizedReactorRoot = reactorRoot.normalize();
        Path resolvedIndexPath = reactorRoot.resolve(indexPath).normalize();
        if (!resolvedIndexPath.startsWith(normalizedReactorRoot)) {
            throw new MojoExecutionException("invalid configuration: indexPath \"" + indexPath
                    + "\" resolves outside the project directory (" + resolvedIndexPath + ")");
        }

        CurrentChanges changes;
        try {
            changes = currentChangesResolver.resolve(reactorRoot, baseRef);
        } catch (IllegalStateException e) {
            throw new MojoExecutionException("invalid configuration: " + e.getMessage(), e);
        }
        IndexApplicability applicability = indexApplicabilityResolver.resolve(resolvedIndexPath, reactorRoot);

        BuildReport.Mode resolvedMode = determineMode(changes, applicability, mode);

        switch (resolvedMode) {
            case TRACK -> runTrack(changes, applicability, reactorRoot, resolvedIndexPath);
            case SELECT -> runSelect(changes, applicability);
            case FALLBACK -> runFallback(applicability);
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

    /**
     * The current build IS the base reference — its own Surefire execution runs
     * completely full and unfiltered, and separately, a subprocess {@code mvn test} (agent
     * attached, via {@link TrackRunner}) (re)builds the index for future {@code SELECT}
     * builds to use (research.md #1, tasks.md T045).
     */
    private void runTrack(CurrentChanges changes, IndexApplicability applicability, Path reactorRoot,
            Path resolvedIndexPath) throws MojoExecutionException {
        Path agentJar = locateCoreAgentJar();
        DependencyIndex freshIndex = trackRunner.track(reactorRoot, agentJar, changes.currentCommit());
        dependencyIndexWriter.write(resolvedIndexPath, freshIndex);

        int totalCount = discoverProjectTests().size();
        BuildReport report = BuildReport.forTrack(applicability.status(), totalCount, freshIndex);
        buildReportWriter.write(reportPath(), report);
        renderReport(report, null);
    }

    /**
     * No valid index and this build is not of the base reference — the full suite runs,
     * safely, but deliberately no track subprocess is forked for this commit (a poor
     * anchor for the shared index) (research.md #1, tasks.md T046).
     */
    private void runFallback(IndexApplicability applicability) throws MojoExecutionException {
        int totalCount = discoverProjectTests().size();
        BuildReport report = BuildReport.forFallback(applicability.status(), totalCount);
        buildReportWriter.write(reportPath(), report);
        renderReport(report, null);
    }

    /**
     * Uses this plugin's own shaded artifact as the tracking agent. The published POM has
     * no {@code blastradius-core} dependency: the engine and its agent entry point are
     * intentionally embedded in this artifact so consumer builds need not resolve an
     * internal companion jar.
     */
    private static Path locateCoreAgentJar() throws MojoExecutionException {
        try {
            Path pluginJar = Path.of(SelectMojo.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (java.nio.file.Files.isRegularFile(pluginJar)) {
                return pluginJar;
            }
        } catch (URISyntaxException | NullPointerException e) {
            throw new MojoExecutionException("could not resolve the shaded plugin jar for tracking", e);
        }
        throw new MojoExecutionException("the tracking agent requires the packaged blastradius plugin jar");
    }

    private Set<TestIdentity> discoverProjectTests() throws MojoExecutionException {
        Path testClassesDir = Path.of(project.getBuild().getTestOutputDirectory());
        List<String> testClasspathElements;
        try {
            testClasspathElements = project.getTestClasspathElements();
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("failed to resolve the test classpath for " + project.getArtifactId(), e);
        }
        return testDiscoverer.discoverAllTests(testClassesDir, testClasspathElements);
    }

    /**
     * A {@code RuntimeException} anywhere in this method's own computation — a corrupted
     * index that passed {@link IndexApplicabilityResolver} but not actual use, or any other
     * unexpected internal fault (tasks.md T050) — must not crash the build or, worse,
     * silently apply a bogus/partial selection. {@link SurefireFilterApplier#apply} runs
     * last, only after decisions are fully computed, so a fault anywhere above it is always
     * caught before any filter is applied — the safe fallback below is exactly what Surefire
     * would have done anyway (run everything) had the goal never run at all.
     */
    private void runSelect(CurrentChanges changes, IndexApplicability applicability) throws MojoExecutionException {
        try {
            Set<TestIdentity> allTests = discoverProjectTests();
            List<SelectionDecision> decisions = computeDecisions(allTests, applicability.index(), changes.changedFiles());
            Set<TestIdentity> selectedTests = decisions.stream()
                    .filter(SelectionDecision::selected)
                    .map(SelectionDecision::test)
                    .collect(Collectors.toSet());

            surefireFilterApplier.apply(project, selectedTests);

            BuildReport report = BuildReport.forSelect(applicability, decisions);
            buildReportWriter.write(reportPath(), report);
            renderReport(report, applicability.index());
        } catch (RuntimeException e) {
            getLog().warn("[blastradius] internal error during selection computation — falling back to running "
                    + "the full suite unfiltered rather than risk a crashed build or an unsound selection", e);
            int totalCount = discoverProjectTests().size();
            BuildReport report = BuildReport.forFallback(IndexApplicability.Status.INTERNAL_ERROR, totalCount);
            buildReportWriter.write(reportPath(), report);
            renderReport(report, null);
        }
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
                .flatMap(file -> file.candidateClassNames().stream())
                .collect(Collectors.toUnmodifiableSet());

        Set<TestIdentity> newOrModifiedTests = allTests.stream()
                .filter(test -> NEW_OR_MODIFIED_TEST_SELECTOR.appliesTo(
                        test, !testDependencies.containsKey(test.baselineKey()), changedClassNames))
                .collect(Collectors.toSet());

        return SELECTION_ENGINE.selectAll(allTests, testDependencies, newOrModifiedTests, changedFiles);
    }
}
