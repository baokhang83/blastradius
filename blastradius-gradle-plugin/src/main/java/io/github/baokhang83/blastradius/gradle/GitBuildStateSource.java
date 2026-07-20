package io.github.baokhang83.blastradius.gradle;

import javax.inject.Inject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

/** Supplies the current and baseline commit as a configuration-cache tracked value. */
public abstract class GitBuildStateSource implements ValueSource<GitBuildState, GitBuildStateSource.Parameters> {

    public interface Parameters extends ValueSourceParameters {

        DirectoryProperty getRepositoryDirectory();

        Property<String> getBaseReference();
    }

    @Inject
    public GitBuildStateSource() {}

    @Override
    public GitBuildState obtain() {
        try (Git git = Git.open(getParameters().getRepositoryDirectory().getAsFile().get())) {
            Repository repository = git.getRepository();
            ObjectId baseCommit = repository.resolve(getParameters().getBaseReference().get());
            ObjectId headCommit = repository.resolve("HEAD");
            if (baseCommit == null) {
                throw new GradleException("base reference \"" + getParameters().getBaseReference().get()
                        + "\" does not resolve to a commit");
            }
            if (headCommit == null) {
                throw new GradleException("HEAD does not resolve to a commit");
            }
            return new GitBuildState(headCommit.getName(), baseCommit.getName());
        } catch (GradleException e) {
            throw e;
        } catch (Exception e) {
            throw new GradleException(
                    "failed to open git repository at " + getParameters().getRepositoryDirectory().getAsFile().get(), e);
        }
    }
}
