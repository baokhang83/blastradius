package io.github.baokhang83.blastradius.gradle;

import java.io.File;
import java.nio.file.Path;
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

        project.getPluginManager().withPlugin("java", ignored -> project.afterEvaluate(ignoredProject -> {
            Path repositoryDirectory = project.getRootDir().toPath();
            Path indexPath = new IndexPathResolver().resolve(project, extension);
            Provider<GitBuildState> gitState = project.getProviders().of(GitBuildStateSource.class, specification -> {
                specification.getParameters().getRepositoryDirectory().set(project.getRootDir());
                specification.getParameters().getBaseReference().set(extension.getBaseRef());
            });
            GitBuildState resolvedGitState = gitState.get();

            project.getTasks().withType(Test.class).configureEach(test -> configureTest(
                    test, repositoryDirectory, indexPath, resolvedGitState, extension.getBaseRef().get()));
        }));
    }

    private static void configureTest(Test test, Path repositoryDirectory, Path indexPath, GitBuildState gitState,
            String baseReference) {
        test.getInputs().property("blastradius.baseReference", baseReference);
        test.getInputs().property("blastradius.headCommit", gitState.headCommit());
        test.getInputs().property("blastradius.baseCommit", gitState.baseCommit());

        if (gitState.baseReferenceBuild()) {
            configureTracking(test, indexPath, gitState.headCommit());
        } else {
            test.getInputs().file(indexPath.toFile()).withPathSensitivity(PathSensitivity.RELATIVE).optional();
            test.doFirst(new ApplySelectionAction(
                    repositoryDirectory.toFile(), indexPath.toFile(), gitState.baseCommit(), gitState.headCommit()));
        }
    }

    private static void configureTracking(Test test, Path indexPath, String anchorCommit) {
        File recordPrefix = new File(test.getTemporaryDir(), "blastradius-dependencies.json");
        File agentJar = new AgentJarLocator().locate().toFile();
        test.jvmArgs("-javaagent:" + agentJar.getAbsolutePath() + "=" + recordPrefix.getAbsolutePath());
        test.getOutputs().upToDateWhen(ignoredTask -> false);
        test.getOutputs().doNotCacheIf("TRACK must execute test workers to produce dependency records", ignoredTask -> true);
        test.doFirst(new PrepareTrackingAction(recordPrefix));
        test.doLast(new WriteTrackingIndexAction(indexPath.toFile(), recordPrefix, anchorCommit));
    }
}
