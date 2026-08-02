# Session: Make test exclusions auditable across validator modes

- **intent:** Make test exclusions auditable across validator modes
- **started:** 2026-08-02

## Decision: Record exclusions as an explicit report boundary

- **where:** `RunConfig, MutationConfig, AnalysisReport, MutationReport, text summary renderers`
- **why:** Both validator modes carry the same opt-in exclusion list and write it into their JSON and text reports, so a passing result states exactly which test classes were not observed.
- **alternative:** Keep exclusions only in Maven command construction and omit them from reports — rejected: that silently changes the evidence population and lets a PASS overstate what was validated.
- **design:** ../design.md#selector-contract
- **constitution:** §III, §V
- **trust:** ✓ verified

## Knowledge transfer

- **RunConfig and MutationConfig:** carry one immutable `SkippedTests` value, defaulting to none for existing callers; both CLI actions parse repeated comma-separated values before constructing their configuration, status: documented.
- **AnalysisReport and MutationReport:** serialize the exact excluded classes in the source-of-truth JSON and copy the list defensively; text summaries render the same boundary beside their high-level totals, status: documented.
- **Interpretation:** a configured exclusion prevents execution in all baseline, head, mutation, and confirmation Maven builds, so it is an opt-in reduction of observed evidence rather than a verdict about those tests, status: documented.
