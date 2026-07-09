package io.github.baokhang83.blastradius.validator.git;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import java.util.List;
import java.util.Objects;

/**
 * A before/after pair of consecutive commits within the analyzed range.
 *
 * @param baseCommit      the earlier commit's SHA
 * @param headCommit      the later commit's SHA; ground truth is captured here
 * @param changedFiles    files that differ between base and head
 * @param status          {@link PairStatus#ANALYZED} or {@link PairStatus#EXCLUDED} (FR-009)
 * @param exclusionReason present only when {@code status = EXCLUDED}
 */
public record CommitPair(
        String baseCommit,
        String headCommit,
        List<ChangedFile> changedFiles,
        PairStatus status,
        String exclusionReason) {

    public CommitPair {
        Objects.requireNonNull(baseCommit, "baseCommit");
        Objects.requireNonNull(headCommit, "headCommit");
        Objects.requireNonNull(changedFiles, "changedFiles");
        Objects.requireNonNull(status, "status");
        changedFiles = List.copyOf(changedFiles);
    }

    /** The common case: a pair that was successfully analyzed. */
    public static CommitPair analyzed(String baseCommit, String headCommit, List<ChangedFile> changedFiles) {
        return new CommitPair(baseCommit, headCommit, changedFiles, PairStatus.ANALYZED, null);
    }

    /** A pair that could not be built/tested (FR-009); carries no changed files. */
    public static CommitPair excluded(String baseCommit, String headCommit, String reason) {
        Objects.requireNonNull(reason, "reason");
        return new CommitPair(baseCommit, headCommit, List.of(), PairStatus.EXCLUDED, reason);
    }
}
