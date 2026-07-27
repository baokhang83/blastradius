package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Guards the one property of the end-to-end harness that silently corrupted real reporting:
 * it rebuilds this very module while this module's own Surefire run is in progress, so it must
 * replace the stale shaded jar without destroying anything else in {@code target/}.
 */
class EndToEndTestSupportTest {

    @Test
    void replacingTheBuiltJarsLeavesTheRunningSurefireReportsIntact(@TempDir Path targetDir) throws Exception {
        // Surefire writes a report per class as that class finishes, but TimingHistoryRecorder only
        // reads the directory once the whole mojo completes. Deleting it midway (as `clean` did)
        // discards every sample gathered so far, and a single missing sample makes BuildReport
        // withhold the time-saved estimate for the entire build.
        Path reports = Files.createDirectory(targetDir.resolve("surefire-reports"));
        Path report = Files.writeString(reports.resolve("TEST-com.example.FooTest.xml"), "<testsuite/>");
        Path classes = Files.createDirectory(targetDir.resolve("classes"));
        Path shadedJar = Files.writeString(targetDir.resolve("blastradius-maven-plugin-0.3.0.jar"), "stale");

        EndToEndTestSupport.deleteBuiltJars(targetDir);

        assertFalse(Files.exists(shadedJar), "the stale shaded jar must go, or the next shade "
                + "execution bootstraps its assembly from it and keeps old embedded core classes");
        assertTrue(Files.exists(report), "an in-progress run's Surefire reports must survive");
        assertTrue(Files.exists(classes), "unrelated build output must survive");
    }

    @Test
    void theInstallCommandNeverCleansTheModuleItIsRunningInside() {
        assertFalse(EndToEndTestSupport.pluginInstallCommand().contains("clean"),
                "`clean` here deletes target/ of the module whose Surefire run invoked it — and with "
                        + "-am, the upstream modules' target/ too, JaCoCo execution data included");
    }
}
