package io.github.baokhang83.blastradius.plugin.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.FileKind;
import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.IndexApplicability;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildReportSchemaTest {

    private final BuildReportWriter writer = new BuildReportWriter();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void writesAStableAuditableSchemaWithACompleteTimingEstimate(@TempDir Path tempDir) throws Exception {
        TestIdentity selected = new TestIdentity("example.FooTest", "checksFoo");
        TestIdentity skipped = new TestIdentity("example.BarTest", "checksBar");
        BuildReport report = BuildReport.forSelect(
                IndexApplicability.applicable(new DependencyIndex("abc123", "2026-07-26T10:00:00Z", List.of())),
                List.of(new ChangedFile("src/main/java/example/Foo.java", FileKind.JAVA_SOURCE, "example.Foo")),
                List.of(SelectionDecision.dependencyMatch(selected, "example.Foo"), SelectionDecision.noMatch(skipped)),
                Map.of(skipped, 1_250L));

        Path output = tempDir.resolve("report.json");
        writer.write(output, report);
        JsonNode json = mapper.readTree(Files.readString(output));

        assertEquals(1, json.path("schemaVersion").asInt());
        assertEquals(1, json.path("changedFiles").size());
        assertEquals(1, json.path("selectedCount").asInt());
        assertEquals(1, json.path("skippedCount").asInt());
        assertEquals(1, json.path("reasonCounts").path("DEPENDENCY_MATCH").asInt());
        assertEquals(1, json.path("reasonCounts").path("NO_MATCH").asInt());
        assertEquals(0, json.path("reasonCounts").path("FALLBACK_NON_SOURCE_CHANGE").asInt());
        assertEquals(1_250L, json.path("estimatedTimeSavedMillis").asLong());
        assertEquals(1, json.path("timingCoverage").path("recordedSkippedTests").asInt());
        assertEquals(1, json.path("timingCoverage").path("totalSkippedTests").asInt());
    }

    @Test
    void withholdsAnIncompleteTimingEstimateRatherThanGuessing() {
        TestIdentity skipped = new TestIdentity("example.BarTest", "checksBar");
        BuildReport report = BuildReport.forSelect(
                IndexApplicability.applicable(new DependencyIndex("abc123", "2026-07-26T10:00:00Z", List.of())),
                List.of(), List.of(SelectionDecision.noMatch(skipped)), Map.of());

        assertEquals(1, report.skippedCount());
        assertEquals(0, report.timingCoverage().recordedSkippedTests());
        assertTrue(report.estimatedTimeSavedMillis() == null);
    }
}
