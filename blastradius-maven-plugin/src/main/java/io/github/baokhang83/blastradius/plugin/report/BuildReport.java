package io.github.baokhang83.blastradius.plugin.report;

import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.IndexApplicability;
import java.util.List;

/**
 * The plugin's per-build output (data-model.md; FR-008, FR-009).
 *
 * @param mode               which path this invocation took (research.md #1)
 * @param indexApplicability why {@code SELECT} was or wasn't chosen
 * @param decisions          one per test; empty for {@code TRACK}/{@code FALLBACK},
 *                           which run the entire suite unconditionally
 * @param selectedCount      tests that actually ran
 * @param totalCount         tests in the full suite
 * @param updatedIndex       present only when {@code mode = TRACK} — the freshly
 *                           (re)built index this run produced
 */
public record BuildReport(
        Mode mode,
        IndexApplicability.Status indexApplicability,
        List<SelectionDecision> decisions,
        int selectedCount,
        int totalCount,
        DependencyIndex updatedIndex) {

    public enum Mode {
        TRACK,
        SELECT,
        FALLBACK
    }
}
