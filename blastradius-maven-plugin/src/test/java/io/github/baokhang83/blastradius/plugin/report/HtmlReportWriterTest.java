package io.github.baokhang83.blastradius.plugin.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class HtmlReportWriterTest {
    @Test
    void rendersSelectionAndEscapesProjectSuppliedText(@TempDir Path dir) throws Exception {
        var index = new DependencyIndex("abc123", "2026-07-26T10:00:00Z", List.of());
        var skipped = new TestIdentity("example.Foo<bar>", "works&fails");
        var report = BuildReport.forSelect(IndexApplicability.applicable(index),
                List.of(new ChangedFile("docs/<script>.md", FileKind.INERT, null)),
                List.of(SelectionDecision.noMatch(skipped)), Map.of());
        Path output = dir.resolve("nested/report.html");
        new HtmlReportWriter().write(output, report);
        String html = Files.readString(output);
        assertTrue(html.contains("docs/&lt;script&gt;.md"));
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("Foo&lt;bar&gt;#works&amp;fails"));
        assertTrue(html.contains("NO_MATCH"));
        assertTrue(html.contains("unavailable (timing history: 0 of 1 skipped tests)"));
    }

    @Test
    void explainsModesWithoutIndividualDecisions(@TempDir Path dir) throws Exception {
        Path output = dir.resolve("report.html");
        new HtmlReportWriter().write(output, BuildReport.forFallback(IndexApplicability.Status.MISSING, 3));
        String html = Files.readString(output);
        assertTrue(html.contains("FALLBACK"));
        assertTrue(html.contains("No per-test decisions were recorded"));
        assertTrue(html.contains("Selected: 3"));
    }
}
