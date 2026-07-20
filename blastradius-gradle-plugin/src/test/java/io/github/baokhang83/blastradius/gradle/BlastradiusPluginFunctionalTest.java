package io.github.baokhang83.blastradius.gradle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlastradiusPluginFunctionalTest {

    @TempDir
    Path projectDir;

    @Test
    void selectsOnlyTheTestThatDependsOnTheChangedClass() throws Exception {
        writeProject();
        String baselineCommit = commitBaseline();
        changeFoo();
        writeIndex(baselineCommit);

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("test", "--stacktrace", "--info")
                .build();

        assertTrue(result.getOutput().contains("[blastradius] SELECT — 1 / 2 tests selected"), result.getOutput());
        assertTrue(Files.exists(projectDir.resolve("build/foo-ran")));
        assertFalse(Files.exists(projectDir.resolve("build/bar-ran")));
    }

    private void writeProject() throws IOException {
        write("settings.gradle", "rootProject.name = 'consumer'\n");
        write("build.gradle", """
                plugins {
                    id 'java'
                    id 'io.github.baokhang83.blastradius'
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    testImplementation platform('org.junit:junit-bom:5.10.2')
                    testImplementation 'org.junit.jupiter:junit-jupiter'
                    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
                }

                blastradius {
                    baseRef = 'main'
                }

                tasks.named('test') {
                    useJUnitPlatform()
                }
                """);
        write("src/main/java/example/Foo.java", "package example; public final class Foo { public int value() { return 1; } }\n");
        write("src/main/java/example/Bar.java", "package example; public final class Bar { public int value() { return 2; } }\n");
        write("src/test/java/example/FooTest.java", """
                package example;

                import java.nio.file.Files;
                import java.nio.file.Path;
                import org.junit.jupiter.api.Test;

                class FooTest {
                    @Test
                    void checksFoo() throws Exception {
                        new Foo().value();
                        Files.writeString(Path.of("build/foo-ran"), "ran");
                    }
                }
                """);
        write("src/test/java/example/BarTest.java", """
                package example;

                import java.nio.file.Files;
                import java.nio.file.Path;
                import org.junit.jupiter.api.Test;

                class BarTest {
                    @Test
                    void checksBar() throws Exception {
                        new Bar().value();
                        Files.writeString(Path.of("build/bar-ran"), "ran");
                    }
                }
                """);
    }

    private String commitBaseline() throws Exception {
        try (Git git = Git.init().setDirectory(projectDir.toFile()).setInitialBranch("main").call()) {
            git.add().addFilepattern(".").call();
            return git.commit().setMessage("baseline").setAuthor("fixture", "fixture@example.invalid").call().getName();
        }
    }

    private void changeFoo() throws Exception {
        write("src/main/java/example/Foo.java", "package example; public final class Foo { public int value() { return 3; } }\n");
        try (Git git = Git.open(projectDir.toFile())) {
            git.checkout().setCreateBranch(true).setName("feature").call();
            git.add().addFilepattern("src/main/java/example/Foo.java").call();
            git.commit().setMessage("change foo").setAuthor("fixture", "fixture@example.invalid").call();
        }
    }

    private void writeIndex(String baselineCommit) throws IOException {
        write(".blastradius/index.json", """
                {
                  "anchorCommit": "%s",
                  "builtAt": "2026-07-20T00:00:00Z",
                  "testDependencies": [
                    {"test": {"className": "example.FooTest", "methodName": "checksFoo"}, "dependsOnClasses": ["example.Foo"]},
                    {"test": {"className": "example.BarTest", "methodName": "checksBar"}, "dependsOnClasses": ["example.Bar"]}
                  ]
                }
                """.formatted(baselineCommit));
    }

    private void write(String relativePath, String content) throws IOException {
        Path destination = projectDir.resolve(relativePath);
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, content);
    }
}
