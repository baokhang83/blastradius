package io.github.baokhang83.blastradius.validator.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunConfigTest {

    @Test
    void validInputsProduceAConfig(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path reportOut = tempDir.resolve("report.json");

        RunConfig config = new RunConfig(repo, 50, reportOut);

        assertEquals(repo, config.projectPath());
        assertEquals(50, config.commitWindowSize());
        assertEquals(reportOut, config.reportOutputPath());
    }

    @Test
    void projectPathMustExist(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist");
        assertThrows(IllegalArgumentException.class,
                () -> new RunConfig(missing, 10, tempDir.resolve("report.json")));
    }

    @Test
    void projectPathMustBeAGitRepository(@TempDir Path tempDir) throws IOException {
        Path notARepo = tempDir.resolve("plain-dir");
        Files.createDirectories(notARepo);
        assertThrows(IllegalArgumentException.class,
                () -> new RunConfig(notARepo, 10, tempDir.resolve("report.json")));
    }

    @Test
    void commitWindowSizeMustBePositive(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        assertThrows(IllegalArgumentException.class,
                () -> new RunConfig(repo, 0, tempDir.resolve("report.json")));
        assertThrows(IllegalArgumentException.class,
                () -> new RunConfig(repo, -5, tempDir.resolve("report.json")));
    }

    @Test
    void reportOutputPathMustNotBeAnExistingDirectory(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path aDirectory = tempDir.resolve("report-as-dir");
        Files.createDirectories(aDirectory);

        assertThrows(IllegalArgumentException.class, () -> new RunConfig(repo, 10, aDirectory));
    }

    private static Path initGitRepo(Path dir) throws Exception {
        Files.createDirectories(dir);
        Git.init().setDirectory(dir.toFile()).call().close();
        return dir;
    }
}
