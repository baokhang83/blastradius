package io.github.baokhang83.blastradius.plugin.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.selection.SelectionReason;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.IndexApplicability;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        DependencyIndex updatedIndex,
        List<ChangedFile> changedFiles,
        Long estimatedTimeSavedMillis,
        TimingCoverage timingCoverage) {

    public static final int SCHEMA_VERSION = 1;

    public BuildReport {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(indexApplicability, "indexApplicability");
        decisions = List.copyOf(decisions);
        changedFiles = List.copyOf(changedFiles);
        Objects.requireNonNull(timingCoverage, "timingCoverage");
        if (selectedCount < 0 || totalCount < 0 || selectedCount > totalCount) {
            throw new IllegalArgumentException("test counts must satisfy 0 <= selectedCount <= totalCount");
        }
        if (estimatedTimeSavedMillis != null && estimatedTimeSavedMillis < 0) {
            throw new IllegalArgumentException("estimatedTimeSavedMillis must not be negative");
        }
    }

    /** Preserves the original construction surface for callers that do not provide timing data. */
    public BuildReport(Mode mode, IndexApplicability.Status indexApplicability, List<SelectionDecision> decisions,
            int selectedCount, int totalCount, DependencyIndex updatedIndex) {
        this(mode, indexApplicability, decisions, selectedCount, totalCount, updatedIndex, List.of(), null,
                TimingCoverage.none());
    }

    public enum Mode {
        TRACK,
        SELECT,
        FALLBACK
    }

    /** Stable schema marker for CI consumers. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public int schemaVersion() {
        return SCHEMA_VERSION;
    }

    /** Tests omitted from the Surefire/Failsafe filter for this invocation. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public int skippedCount() {
        return totalCount - selectedCount;
    }

    /** Includes every enum value so consumers never need to infer absent zero buckets. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Map<SelectionReason, Integer> reasonCounts() {
        Map<SelectionReason, Integer> counts = new EnumMap<>(SelectionReason.class);
        for (SelectionReason reason : SelectionReason.values()) {
            counts.put(reason, 0);
        }
        decisions.forEach(decision -> counts.merge(decision.reason(), 1, Integer::sum));
        return Map.copyOf(counts);
    }

    /**
     * Builds the {@code SELECT}-mode report from the engine's own per-test decisions
     * (tasks.md T034) — {@code totalCount}/{@code selectedCount} are derived from
     * {@code decisions} itself, never passed in separately, so they can never drift out of
     * sync with it (contracts/mojo-and-index-contract.md's invariants).
     */
    public static BuildReport forSelect(IndexApplicability applicability, List<SelectionDecision> decisions) {
        return forSelect(applicability, List.of(), decisions, Map.of());
    }

    /**
     * Builds a SELECT-mode report with the complete changed-file context and a duration-based
     * savings estimate when each skipped test has a persisted timing sample.
     */
    public static BuildReport forSelect(IndexApplicability applicability, List<ChangedFile> changedFiles,
            List<SelectionDecision> decisions, Map<TestIdentity, Long> durationsByTest) {
        Objects.requireNonNull(applicability, "applicability");
        Objects.requireNonNull(changedFiles, "changedFiles");
        Objects.requireNonNull(decisions, "decisions");
        Objects.requireNonNull(durationsByTest, "durationsByTest");
        int selectedCount = (int) decisions.stream().filter(SelectionDecision::selected).count();
        List<SelectionDecision> skipped = decisions.stream().filter(decision -> !decision.selected()).toList();
        int recordedSkippedTests = (int) skipped.stream().filter(decision -> durationsByTest.containsKey(decision.test())).count();
        Long estimatedTimeSavedMillis = recordedSkippedTests == skipped.size()
                ? skipped.stream().mapToLong(decision -> durationsByTest.get(decision.test())).sum()
                : null;
        return new BuildReport(Mode.SELECT, applicability.status(), decisions, selectedCount, decisions.size(), null,
                changedFiles, estimatedTimeSavedMillis, new TimingCoverage(recordedSkippedTests, skipped.size()));
    }

    /**
     * Builds the {@code TRACK}-mode report (tasks.md T045) — the full suite ran
     * unconditionally (no per-test decisions computed), and {@code updatedIndex} is the
     * freshly (re)built index this run produced.
     */
    public static BuildReport forTrack(IndexApplicability.Status indexApplicability, int totalCount,
            DependencyIndex updatedIndex) {
        return forTrack(indexApplicability, totalCount, updatedIndex, List.of());
    }

    public static BuildReport forTrack(IndexApplicability.Status indexApplicability, int totalCount,
            DependencyIndex updatedIndex, List<ChangedFile> changedFiles) {
        Objects.requireNonNull(indexApplicability, "indexApplicability");
        Objects.requireNonNull(updatedIndex, "updatedIndex");
        return new BuildReport(Mode.TRACK, indexApplicability, List.of(), totalCount, totalCount, updatedIndex,
                changedFiles, 0L, TimingCoverage.none());
    }

    /**
     * Builds the {@code FALLBACK}-mode report (tasks.md T046) — the full suite ran
     * unconditionally, and deliberately no index is produced or refreshed (research.md #1).
     */
    public static BuildReport forFallback(IndexApplicability.Status indexApplicability, int totalCount) {
        return forFallback(indexApplicability, totalCount, List.of());
    }

    public static BuildReport forFallback(IndexApplicability.Status indexApplicability, int totalCount,
            List<ChangedFile> changedFiles) {
        Objects.requireNonNull(indexApplicability, "indexApplicability");
        return new BuildReport(Mode.FALLBACK, indexApplicability, List.of(), totalCount, totalCount, null,
                changedFiles, 0L, TimingCoverage.none());
    }
}
