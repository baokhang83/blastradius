package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.baokhang83.blastradius.plugin.diff.CurrentChanges;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.IndexApplicability;
import io.github.baokhang83.blastradius.plugin.report.BuildReport;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SelectMojoModeRoutingTest {

    private static final CurrentChanges BASE_REF_BUILD =
            new CurrentChanges("main", "abc123", Optional.of("abc123"), "abc123", true, List.of());
    private static final CurrentChanges PR_BUILD =
            new CurrentChanges("main", "abc123", Optional.of("abc123"), "def456", false, List.of());

    @Test
    void baseRefBuildAlwaysRoutesToTrack() {
        IndexApplicability applicable = IndexApplicability.applicable(
                new DependencyIndex("abc123", "2026-07-09T10:00:00Z", List.of()));

        BuildReport.Mode mode = SelectMojo.determineMode(BASE_REF_BUILD, applicable, null);

        assertEquals(BuildReport.Mode.TRACK, mode);
    }

    @Test
    void explicitTrackModeFlagRoutesToTrackEvenOnAPrBuild() {
        IndexApplicability applicable = IndexApplicability.applicable(
                new DependencyIndex("abc123", "2026-07-09T10:00:00Z", List.of()));

        BuildReport.Mode mode = SelectMojo.determineMode(PR_BUILD, applicable, "track");

        assertEquals(BuildReport.Mode.TRACK, mode);
    }

    @Test
    void prBuildWithApplicableIndexRoutesToSelect() {
        IndexApplicability applicable = IndexApplicability.applicable(
                new DependencyIndex("abc123", "2026-07-09T10:00:00Z", List.of()));

        BuildReport.Mode mode = SelectMojo.determineMode(PR_BUILD, applicable, null);

        assertEquals(BuildReport.Mode.SELECT, mode);
    }

    @Test
    void prBuildWithMissingIndexRoutesToFallback() {
        BuildReport.Mode mode = SelectMojo.determineMode(PR_BUILD, IndexApplicability.missing(), null);

        assertEquals(BuildReport.Mode.FALLBACK, mode);
    }

    @Test
    void prBuildWithUnreadableIndexRoutesToFallback() {
        BuildReport.Mode mode = SelectMojo.determineMode(PR_BUILD, IndexApplicability.unreadable(), null);

        assertEquals(BuildReport.Mode.FALLBACK, mode);
    }

    @Test
    void prBuildWithUnreachableAnchorRoutesToFallback() {
        BuildReport.Mode mode = SelectMojo.determineMode(PR_BUILD, IndexApplicability.anchorUnreachable(), null);

        assertEquals(BuildReport.Mode.FALLBACK, mode);
    }

    @Test
    void prBuildWithoutAMergeBaseRoutesToFallback() {
        BuildReport.Mode mode = SelectMojo.determineMode(PR_BUILD, IndexApplicability.mergeBaseUnavailable(), null);

        assertEquals(BuildReport.Mode.FALLBACK, mode);
    }
}
