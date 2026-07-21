package io.github.baokhang83.blastradius.plugin.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.IndexApplicability;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests (tasks.md T035) for the console summary renderer — given a {@link BuildReport},
 * produces the {@code [blastradius] ...} lines per contracts/mojo-and-index-contract.md's
 * "Console summary" section (mode, index anchor + timestamp, selected/total counts,
 * per-reason breakdown).
 */
class ConsoleSummaryRendererTest {

    private final ConsoleSummaryRenderer renderer = new ConsoleSummaryRenderer();

    @Test
    void selectModeMatchesTheContractExample() {
        List<SelectionDecision> decisions = decisionsOfSizes(33, 8, 0, 55); // matched, new/modified, fallback, noMatch
        BuildReport report = BuildReport.forSelect(
                IndexApplicability.applicable(new DependencyIndex("a1b2c3d4e5f", "2026-07-09T10:03:00Z", List.of())),
                decisions);

        List<String> lines = renderer.render(report,
                IndexApplicability.applicable(new DependencyIndex("a1b2c3d4e5f", "2026-07-09T10:03:00Z", List.of())).index());

        assertEquals(List.of(
                "[blastradius] SELECT — index built from a1b2c3d (2026-07-09T10:03:00Z)",
                "[blastradius] 41 / 96 tests selected (57.3% skipped)",
                "[blastradius]   dependency-matched: 33, new-or-modified: 8, fallback: 0",
                "[blastradius] Skipped test detail: run with -Dblastradius.explain=true for the full per-test reasoning"),
                lines);
    }

    @Test
    void trackModeHasNoPerReasonBreakdown() {
        BuildReport report = new BuildReport(BuildReport.Mode.TRACK, IndexApplicability.Status.MISSING, List.of(), 12, 12, null);

        List<String> lines = renderer.render(report, null);

        assertTrue(lines.get(0).startsWith("[blastradius] TRACK"));
        assertEquals("[blastradius] 12 / 12 tests selected (0.0% skipped)", lines.get(1));
        assertEquals(2, lines.size(), "TRACK runs everything — no per-reason breakdown to render");
    }

    @Test
    void fallbackModeExplainsWhyNoIndexApplied() {
        BuildReport report = new BuildReport(BuildReport.Mode.FALLBACK, IndexApplicability.Status.ANCHOR_UNREACHABLE, List.of(), 20, 20, null);

        List<String> lines = renderer.render(report, null);

        assertTrue(lines.get(0).startsWith("[blastradius] FALLBACK"));
        assertTrue(lines.get(0).contains("ANCHOR_UNREACHABLE") || lines.get(0).toLowerCase().contains("anchor"),
                "expected the fallback reason to be surfaced: " + lines.get(0));
    }

    @Test
    void fallbackModeNamesAnUnsupportedIndexFormat() {
        BuildReport report = new BuildReport(
                BuildReport.Mode.FALLBACK, IndexApplicability.Status.FORMAT_VERSION_MISMATCH, List.of(), 20, 20, null);

        List<String> lines = renderer.render(report, null);

        assertTrue(lines.get(0).contains("FORMAT_VERSION_MISMATCH"), lines.get(0));
    }

    private static List<SelectionDecision> decisionsOfSizes(int matched, int newOrModified, int fallback, int noMatch) {
        List<SelectionDecision> decisions = new java.util.ArrayList<>();
        for (int i = 0; i < matched; i++) {
            decisions.add(SelectionDecision.dependencyMatch(
                    new TestIdentity("com.example.Matched" + i, "test"), "com.example.Changed" + i));
        }
        for (int i = 0; i < newOrModified; i++) {
            decisions.add(SelectionDecision.newOrModifiedTest(new TestIdentity("com.example.New" + i, "test")));
        }
        for (int i = 0; i < fallback; i++) {
            decisions.add(SelectionDecision.fallback(new TestIdentity("com.example.Fallback" + i, "test")));
        }
        for (int i = 0; i < noMatch; i++) {
            decisions.add(SelectionDecision.noMatch(new TestIdentity("com.example.Skipped" + i, "test")));
        }
        return decisions;
    }
}
