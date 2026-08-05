package io.github.baokhang83.blastradius.gradle;

import java.io.File;
import java.nio.file.Path;
import io.github.baokhang83.blastradius.core.index.CommitIndexKey;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.testing.Test;

/** Configures dependency-based selection for Java {@link Test} tasks. */
public final class BlastradiusPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        BlastradiusExtension extension = project.getExtensions()
                .create("blastradius", BlastradiusExtension.class);
        extension.getIndexPath().convention(".blastradius/index.json");
        extension.getDirectInvocationFallback().convention(true);
        extension.getIndexStore().convention("file");
        extension.getS3Prefix().convention("");

        project.getPluginManager().withPlugin("java", ignored -> project.afterEvaluate(ignoredProject -> {
            Path repositoryDirectory = project.getRootDir().toPath();
            Path indexPath = new IndexPathResolver().resolve(project, extension);
            Provider<GitBuildState> gitState = project.getProviders().of(GitBuildStateSource.class, specification -> {
                specification.getParameters().getRepositoryDirectory().set(project.getRootDir());
                specification.getParameters().getBaseReference().set(extension.getBaseRef());
            });
            GitBuildState resolvedGitState = gitState.get();
            Path normalizedRepositoryDirectory = repositoryDirectory.toAbsolutePath().normalize();
            String indexPathKey = normalizedRepositoryDirectory.relativize(indexPath.toAbsolutePath().normalize()).toString();
            ConfiguredIndexStore indexStore = new ConfiguredIndexStore(extension.getIndexStore().get(), extension.getS3Bucket().getOrNull(),
                    extension.getS3Prefix().get(), extension.getS3Region().getOrNull(), extension.getS3Endpoint().getOrNull());

            project.getTasks().withType(Test.class).configureEach(test -> configureTest(
                    project, test, repositoryDirectory, indexPathKey, resolvedGitState, extension.getBaseRef().get(),
                    indexStore, extension));
        }));
    }

    private static void configureTest(Project project, Test test, Path repositoryDirectory, String indexPathKey, GitBuildState gitState,
            String baseReference, ConfiguredIndexStore indexStore, BlastradiusExtension extension) {
        test.getInputs().property("blastradius.baseReference", baseReference);
        test.getInputs().property("blastradius.headCommit", gitState.headCommit());
        test.getInputs().property("blastradius.baseReferenceCommit", gitState.baseReferenceCommit());
        test.getInputs().property("blastradius.directInvocationFallback", extension.getDirectInvocationFallback());

        if (gitState.baseReferenceBuild()) {
            configureTracking(test, repositoryDirectory, indexPathKey, gitState.headCommit(), indexStore);
        } else if (!gitState.comparisonBaseAvailable()) {
            test.doFirst(new NoComparisonBaseFallbackAction());
        } else {
            String comparisonBase = gitState.comparisonBaseCommit();
            test.getInputs().property("blastradius.comparisonBaseCommit", comparisonBase);
            Path baselineIndexPath = repositoryDirectory.resolve(
                    CommitIndexKey.forCommit(indexPathKey, comparisonBase));
            test.getInputs()
                    .files(project.files(baselineIndexPath.toFile()).filter(File::isFile))
                    .withPathSensitivity(PathSensitivity.RELATIVE);
            test.doFirst(new ApplySelectionAction(
                    repositoryDirectory.toFile(), indexPathKey, comparisonBase, gitState.headCommit(), indexStore,
                    extension.getDirectInvocationFallback().get()));
        }
        
    }

    private static void configureTracking(Test test, Path repositoryDirectory, String indexPathKey, String anchorCommit, ConfiguredIndexStore indexStore) {
        File recordPrefix = new File(test.getTemporaryDir(), "blastradius-dependencies.json");
        File agentJar = new AgentJarLocator().locate().toFile();
        test.jvmArgs("-javaagent:" + agentJar.getAbsolutePath() + "=" + recordPrefix.getAbsolutePath());
        test.getOutputs().upToDateWhen(ignoredTask -> false);
        test.getOutputs().doNotCacheIf("TRACK must execute test workers to produce dependency records", ignoredTask -> true);
        test.doFirst(new PrepareTrackingAction(recordPrefix));
        test.doLast(new WriteTrackingIndexAction(
                repositoryDirectory.toFile(), indexPathKey, recordPrefix, anchorCommit, indexStore));
    }
}
