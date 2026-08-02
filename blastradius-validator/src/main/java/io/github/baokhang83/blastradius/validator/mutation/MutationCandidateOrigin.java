package io.github.baokhang83.blastradius.validator.mutation;

/**
 * Which candidate pool a mutant came from — see
 * {@link HistoricalMutationValidator#validate}. A pair's mutants are either all
 * {@link #DIFF_TARGETED} (mutated within the commit's own changed production sources) or all
 * {@link #WHOLE_TREE_FALLBACK} (the diff carried no mutable production source, so the validator
 * fell back to the whole-tree scan rather than validating nothing — see the constitution's
 * narrowed-corpus-must-fall-back principle). The two ask different questions — "would selection
 * catch a bug in what this PR touched" vs. "would it catch a bug somewhere else entirely" — so
 * evidence from each is worth reporting separately rather than only as a pooled total.
 */
public enum MutationCandidateOrigin {
    DIFF_TARGETED,
    WHOLE_TREE_FALLBACK
}
