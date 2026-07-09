package io.github.baokhang83.blastradius.plugin.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.IndexApplicability;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildReportIoTest {

    private final BuildReportWriter writer = new BuildReportWriter();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void writtenSelectModeReportRoundTripsThroughAFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("report.json");
        TestIdentity fooTest = new TestIdentity("com.example.FooTest", "checksAdd");
        SelectionDecision decision = SelectionDecision.dependencyMatch(fooTest, "com.example.Foo");
        BuildReport original = new BuildReport(
                BuildReport.Mode.SELECT, IndexApplicability.Status.APPLICABLE,
                List.of(decision), 1, 5, null);

        writer.write(file, original);
        assertTrue(Files.exists(file));

        BuildReport roundTripped = mapper.readValue(file.toFile(), BuildReport.class);

        assertEquals(original, roundTripped);
    }

    @Test
    void trackModeReportHasEmptyDecisionsAndAnUpdatedIndex(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("track-report.json");
        DependencyIndex updatedIndex = new DependencyIndex("a1b2c3d", "2026-07-09T10:00:00Z", List.of());
        BuildReport original = new BuildReport(
                BuildReport.Mode.TRACK, IndexApplicability.Status.MISSING, List.of(), 5, 5, updatedIndex);

        writer.write(file, original);
        BuildReport roundTripped = mapper.readValue(file.toFile(), BuildReport.class);

        assertEquals(original, roundTripped);
        assertEquals(5, roundTripped.selectedCount());
        assertTrue(roundTripped.decisions().isEmpty());
    }
}
