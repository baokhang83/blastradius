package io.github.baokhang83.blastradius.plugin.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes a self-contained view of the same selection data as the JSON report. */
public final class HtmlReportWriter {
    public void write(Path output, BuildReport report) {
        StringBuilder html = new StringBuilder("""
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Blastradius selection report</title><style>
                body{font:16px system-ui,sans-serif;max-width:1100px;margin:2rem auto;padding:0 1rem;color:#202833}
                table{border-collapse:collapse;width:100%;margin:1rem 0}th,td{border-bottom:1px solid #d6dce2;padding:.55rem;text-align:left;overflow-wrap:anywhere}
                th{background:#f2f5f7}code{font-size:.9em}.summary{display:flex;gap:2rem;flex-wrap:wrap}
                </style></head><body><h1>Blastradius selection report</h1>
                """);
        html.append("<p>Mode: <strong>").append(escape(report.mode().name()))
                .append("</strong> · Index: ").append(escape(report.indexApplicability().name())).append("</p>");
        html.append("<div class=\"summary\"><p>Selected: ").append(report.selectedCount())
                .append("</p><p>Skipped: ").append(report.skippedCount()).append("</p><p>Total: ")
                .append(report.totalCount()).append("</p></div><p>Estimated time saved: ");
        if (report.estimatedTimeSavedMillis() == null) {
            html.append("unavailable (timing history: ").append(report.timingCoverage().recordedSkippedTests())
                    .append(" of ").append(report.timingCoverage().totalSkippedTests()).append(" skipped tests)");
        } else {
            html.append(report.estimatedTimeSavedMillis()).append(" ms");
        }
        html.append("</p><h2>Changed files</h2><table><thead><tr><th>Path</th><th>Kind</th><th>Class</th></tr></thead><tbody>");
        report.changedFiles().forEach(file -> html.append("<tr><td><code>").append(escape(file.path()))
                .append("</code></td><td>").append(escape(file.kind().name())).append("</td><td>")
                .append(escape(file.changedClassName())).append("</td></tr>"));
        html.append("</tbody></table><h2>Test decisions</h2>");
        if (report.decisions().isEmpty()) {
            html.append("<p>No per-test decisions were recorded for this build mode.</p>");
        } else {
            html.append("<table><thead><tr><th>Test</th><th>Result</th><th>Reason</th><th>Changed class</th><th>Invocation path</th></tr></thead><tbody>");
            report.decisions().forEach(decision -> html.append("<tr><td><code>")
                    .append(escape(decision.test().className())).append("#").append(escape(decision.test().methodName()))
                    .append("</code></td><td>").append(decision.selected() ? "Selected" : "Skipped")
                    .append("</td><td>").append(escape(decision.reason().name())).append("</td><td>")
                    .append(escape(decision.matchedChangedClass())).append("</td><td>")
                    .append(escape(decision.directInvocationSourceClass()))
                    .append(decision.directInvocationIntermediateClass() == null ? "" : " → " + escape(decision.directInvocationIntermediateClass()))
                    .append("</td></tr>"));
            html.append("</tbody></table>");
        }
        html.append("</body></html>\n");
        try {
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            Files.writeString(output, html, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write HTML report to " + output, e);
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
