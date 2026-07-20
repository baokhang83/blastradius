package io.github.baokhang83.blastradius.plugin.report;

import io.github.baokhang83.blastradius.core.selection.SelectionReason;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.IndexApplicability;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a {@link BuildReport} as the {@code [blastradius] ...} console lines
 * contracts/mojo-and-index-contract.md's "Console summary" section specifies — a rendering
 * of the JSON report, never a second source of truth (FR-008, FR-009).
 */
public final class ConsoleSummaryRenderer {

    /**
     * @param usedIndex the index this build actually used, only meaningful (and only
     *                   passed non-null) in {@code SELECT} mode — {@code TRACK} is building
     *                   a fresh one, {@code FALLBACK} used none
     */
    public List<String> render(BuildReport report, DependencyIndex usedIndex) {
        List<String> lines = new ArrayList<>();
        lines.add("[blastradius] " + headerLine(report, usedIndex));

        int skipped = report.totalCount() - report.selectedCount();
        double skippedPercent = report.totalCount() == 0 ? 0.0 : 100.0 * skipped / report.totalCount();
        lines.add(String.format("[blastradius] %d / %d tests selected (%.1f%% skipped)",
                report.selectedCount(), report.totalCount(), skippedPercent));

        if (report.mode() == BuildReport.Mode.SELECT) {
            long dependencyMatched = countReason(report, SelectionReason.DEPENDENCY_MATCH);
            long newOrModified = countReason(report, SelectionReason.NEW_OR_MODIFIED_TEST);
            long fallback = countReason(report, SelectionReason.FALLBACK_NON_SOURCE_CHANGE);
            lines.add(String.format("[blastradius]   dependency-matched: %d, new-or-modified: %d, fallback: %d",
                    dependencyMatched, newOrModified, fallback));
            lines.add("[blastradius] Skipped test detail: run with -Dblastradius.explain=true for the full per-test reasoning");
        }
        return lines;
    }

    private static String headerLine(BuildReport report, DependencyIndex usedIndex) {
        return switch (report.mode()) {
            case SELECT -> "SELECT — index built from " + shortSha(usedIndex.anchorCommit()) + " (" + usedIndex.builtAt() + ")";
            case TRACK -> "TRACK — building a fresh index";
            case FALLBACK -> "FALLBACK — " + fallbackReason(report.indexApplicability());
        };
    }

    private static String fallbackReason(IndexApplicability.Status status) {
        return switch (status) {
            case MISSING -> "no persisted index found (MISSING)";
            case UNREADABLE -> "persisted index could not be read (UNREADABLE)";
            case ANCHOR_UNREACHABLE -> "persisted index's anchor commit is no longer reachable (ANCHOR_UNREACHABLE)";
            case ANCHOR_MISMATCH -> "persisted index does not match the resolved baseline commit (ANCHOR_MISMATCH)";
            case INTERNAL_ERROR -> "an internal error occurred during selection computation (INTERNAL_ERROR)";
            case APPLICABLE -> throw new IllegalStateException("FALLBACK mode never has an APPLICABLE index");
        };
    }

    private static String shortSha(String sha) {
        return sha.length() > 7 ? sha.substring(0, 7) : sha;
    }

    private static long countReason(BuildReport report, SelectionReason reason) {
        return report.decisions().stream().filter(d -> d.reason() == reason).count();
    }
}
