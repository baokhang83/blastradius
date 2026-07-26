package io.github.baokhang83.blastradius.plugin.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SurefireReportReaderTest {

    private final SurefireReportReader reader = new SurefireReportReader();

    @Test
    void readsPerTestDurationsFromStandardSurefireXml(@TempDir Path tempDir) throws Exception {
        Path reports = Files.createDirectory(tempDir.resolve("surefire-reports"));
        Files.writeString(reports.resolve("TEST-example.FooTest.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="example.FooTest">
                  <testcase name="fast" classname="example.FooTest" time="0.006"/>
                  <testcase name="slow" classname="example.FooTest" time="1.234"/>
                </testsuite>
                """);

        Map<TestIdentity, Long> durations = reader.read(reports);

        assertEquals(Map.of(
                new TestIdentity("example.FooTest", "fast"), 6L,
                new TestIdentity("example.FooTest", "slow"), 1_234L), durations);
    }
}
