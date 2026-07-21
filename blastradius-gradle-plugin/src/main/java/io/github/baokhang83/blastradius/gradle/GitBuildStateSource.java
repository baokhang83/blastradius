package io.github.baokhang83.blastradius.gradle;

import io.github.baokhang83.blastradius.core.git.GitComparison;
import io.github.baokhang83.blastradius.core.git.MergeBaseResolver;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

/** Supplies HEAD, the configured target tip, and their comparison base as configuration-cache state. */
public abstract class GitBuildStateSource implements ValueSource<GitBuildState, GitBuildStateSource.Parameters> {

    public interface Parameters extends ValueSourceParameters {

        DirectoryProperty getRepositoryDirectory();

        Property<String> getBaseReference();
    }

    @Inject
    public GitBuildStateSource() {}

    @Override
    public GitBuildState obtain() {
        try {
            GitComparison comparison = new MergeBaseResolver().resolve(
                    getParameters().getRepositoryDirectory().getAsFile().get().toPath(),
                    getParameters().getBaseReference().get());
            return new GitBuildState(
                    comparison.headCommit(),
                    comparison.baseReferenceCommit(),
                    comparison.comparisonBaseCommit().orElse(null));
        } catch (GradleException e) {
            throw e;
        } catch (Exception e) {
            throw new GradleException(
                    "failed to open git repository at " + getParameters().getRepositoryDirectory().getAsFile().get(), e);
        }
    }
}
