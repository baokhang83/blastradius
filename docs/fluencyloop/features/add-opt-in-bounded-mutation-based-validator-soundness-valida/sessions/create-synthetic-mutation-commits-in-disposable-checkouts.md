# Session: Create synthetic mutation commits in disposable checkouts

- **intent:** Create synthetic mutation commits in disposable checkouts
- **started:** 2026-08-01

## Decision: Create mutations as real commits in the existing disposable-clone lifecycle

- **where:** `CommitCheckout` and `SyntheticMutationCheckout`
- **why:** A committed child gives the selector its normal Git edge while one established cleanup path proves the target worktree is never mutated.
- **alternative:** Patch the target project then revert it, or create a second ad hoc clone implementation — rejected: both make accidental state leakage and lifecycle drift more likely.
- **design:** [sequence: one mutation experiment](../design.md#sequence-one-mutation-experiment)
- **constitution:** §III, §VII
- **trust:** ✓ verified

## Knowledge transfer

The key distinction is between a source-file edit and a Git edge. The selector reasons over the diff from a base SHA to a head SHA, so a synthetic commit makes the experiment exercise that exact production path rather than an approximation. The checkout test verifies both sides of the safety property: `M` names `B` as its direct parent, and the source repository remains at its original SHA with a clean worktree. The synthetic author identity exists only in the disposable clone and has no effect on the project under analysis.
