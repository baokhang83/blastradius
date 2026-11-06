package io.github.baokhang83.blastradius.plugin.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.IndexApplicability;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetricsHistoryWriterTest {
    @Test
    void appendsIndependentTimestampedSamplesWithoutInventingMissingMetrics(@TempDir Path dir) throws Exception {
        var writer = new MetricsHistoryWriter();
        var index = new DependencyIndex("abc123", "2026-07-26T10:00:00Z", List.of());
        var report = BuildReport.forSelect(IndexApplicability.applicable(index), List.of(),
                List.of(SelectionDecision.noMatch(new TestIdentity("example.FooTest", "test"))), Map.of());
        Path path = dir.resolve("nested/metrics.jsonl");
        writer.append(path, report);
        writer.append(path, BuildReport.forFallback(IndexApplicability.Status.MISSING, 3));

        var lines = Files.readAllLines(path);
        assertEquals(2, lines.size());
        var mapper = new ObjectMapper();
        var first = mapper.readTree(lines.get(0));
        assertEquals(1, first.path("schemaVersion").asInt());
        Instant.parse(first.path("recordedAt").asText());
        assertEquals(1, first.path("skippedCount").asInt());
        assertTrue(first.path("estimatedTimeSavedMillis").isNull());
        assertTrue(first.path("auditMisses").isNull());
        var second = mapper.readTree(lines.get(1));
        assertEquals("FALLBACK", second.path("mode").asText());
        assertEquals(3, second.path("selectedCount").asInt());
        assertEquals(0, second.path("estimatedTimeSavedMillis").asLong());
    }
}
