# Session: Resolve a PR comparison base in shared core

- **intent:** Resolve a PR comparison base in shared core
- **started:** 2026-07-21

## Decision: Use one graph-aware resolver for both build integrations

- **where:** `blastradius-core/src/main/java/io/github/baokhang83/blastradius/core/git/MergeBaseResolver.java; blastradius-core/src/main/java/io/github/baokhang83/blastradius/core/git/GitComparison.java`
- **why:** Maven and Gradle must calculate the same comparison base; one JGit-backed resolver returns the target tip separately from the merge base and exposes no-common-ancestor explicitly.
- **alternative:** Resolve the target tip and diff it directly, or duplicate merge-base traversal in each plugin — rejected because target-only commits leak into PR selection and duplicate graph logic can diverge.
- **design:** ../design.md#class-diagram
- **constitution:** §II, §III, §IV
- **trust:** ✓ verified

## Knowledge transfer

- **GitComparison:** preserves three distinct values: the build's `HEAD`, the configured
  reference's current tip, and an optional common ancestor. `baseReferenceBuild()` compares the
  first two only, so a branch whose merge base happens to equal `HEAD` never becomes a TRACK
  build by accident. **Status:** verified by the base-reference fixture.
- **MergeBaseResolver:** uses JGit's `RevFilter.MERGE_BASE` over the two resolved commits and
  returns an empty comparison base for unrelated histories. Callers can then take the safe,
  explicit fallback rather than inventing a diff endpoint. **Status:** verified by the divergent
  history, merge-commit, rebase, and unrelated-history fixtures.
