# Session: measure newly confirmed failure coverage per edge

- **intent:** measure newly confirmed failure coverage per edge
- **started:** 2026-08-01

## Knowledge transfer

- **FailureCoverage:** records the observed, newly confirmed failure denominator and partitions
  it into selected and skipped tests. Its constructor enforces non-negative values and the
  selected-plus-skipped invariant, so a report cannot claim a denominator inconsistent with the
  individual miss evidence. · status: documented
- **FailureComparison:** is the comparator's per-edge result. It couples `WouldMissCase` detail
  to the aggregate coverage and verifies that every skipped failure has an individual case. Base
  failures and flaky head outcomes do not enter the new-failure denominator. · status: documented

## Decision: couple failure coverage to comparator evidence

- **where:** `WouldMissComparator, FailureComparison, and FailureCoverage`
- **why:** the report needs a visible observed-failure denominator that cannot drift from the individual skipped failures
- **alternative:** Infer totals from the would-miss list or keep independent counters — rejected: zero misses cannot distinguish full coverage from no observed failures and detached counters can disagree
- **design:** ../design.md#failure-bearing-coverage-is-a-first-class-report-result
- **constitution:** §III
- **trust:** ✓ verified
