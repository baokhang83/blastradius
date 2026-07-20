package io.github.baokhang83.blastradius.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.testing.Test;

/** Configures dependency-based selection for Java {@link Test} tasks. */
public final class BlastradiusPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        BlastradiusExtension extension = project.getExtensions()
                .create("blastradius", BlastradiusExtension.class);
        extension.getIndexPath().convention(".blastradius/index.json");

        project.getPluginManager().withPlugin("java", ignored -> project.getTasks()
                .withType(Test.class)
                .configureEach(test -> test.doFirst(ignoredTask -> new GradleSelectAction(project, extension).apply(test))));
    }
}
