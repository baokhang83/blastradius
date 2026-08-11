package io.github.baokhang83.blastradius.plugin.mojo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/** Writes a minimal greeting to Maven's normal build log. */
@Mojo(name = "hello")
public final class HelloMojo extends AbstractMojo {

    static final String MESSAGE = "Hello, world!";

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info(MESSAGE);
    }

    static String message() {
        return MESSAGE;
    }
}
