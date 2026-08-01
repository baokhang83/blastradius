# Session: mark adjacency-based analyses superseded

- **intent:** mark adjacency-based analyses superseded
- **started:** 2026-08-01

## Knowledge transfer

- **Historical validation table:** records throughput evidence but formerly used `RevWalk`
  adjacency, so its pairs are not guaranteed to be real changes. Keeping the figures labelled as
  superseded preserves transparency without claiming they establish selection soundness. New
  reports carry both replay mode and the observed-failure denominator. · status: documented

## Decision: retain but supersede adjacency-based analysis results

- **where:** `README historical validation section`
- **why:** past savings measurements remain useful context but cannot support soundness claims without real edges and an observed-failure denominator
- **alternative:** Delete the results or leave them presented as validation evidence — rejected: deletion loses provenance and the old wording overclaims what the runs establish
- **design:** ../design.md#report-compatibility-and-evidence-wording
- **constitution:** §V
- **trust:** ✓ verified
