package io.github.baokhang83.blastradius.plugin.diff;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import java.util.List;

/**
 * The live build's own analog of the validator's {@code CommitPair}/{@code ChangedFile} —
 * computed once per {@code blastradius:select} invocation, not persisted (data-model.md).
 *
 * @param baseReference     the configured base git reference, as given (branch name or SHA)
 * @param resolvedBaseCommit {@code baseReference} resolved to a concrete commit
 * @param currentCommit     the commit actually being built (HEAD)
 * @param isBaseRefBuild    {@code currentCommit == resolvedBaseCommit} — this build IS the
 *                          base reference (research.md #1's trigger for TRACK mode)
 * @param changedFiles      diff between {@code resolvedBaseCommit} and {@code currentCommit};
 *                          empty when {@code isBaseRefBuild = true}
 */
public record CurrentChanges(
        String baseReference,
        String resolvedBaseCommit,
        String currentCommit,
        boolean isBaseRefBuild,
        List<ChangedFile> changedFiles) {}
