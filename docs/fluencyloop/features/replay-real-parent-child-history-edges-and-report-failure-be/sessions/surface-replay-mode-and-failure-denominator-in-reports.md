# Session: surface replay mode and failure denominator in reports

- **intent:** surface replay mode and failure denominator in reports
- **started:** 2026-08-01

## Knowledge transfer

- **RunConfig and Main:** accept `--history-mode all-parents|first-parent`, defaulting to
  `ALL_PARENTS`, and carry the enum into the resolver. The enum parser accepts either kebab-case
  CLI spelling or enum spelling, while rejecting an unrecognised mode. · status: documented
- **AnalysisReport and TextSummaryRenderer:** serialize the replay mode and print a failure
  coverage block before individual would-miss detail. Excluded pairs and flaky failures are
  explicit summary counts, so zero misses cannot hide an empty observed-failure denominator.
  · status: documented

## Decision: serialize replay policy and observed failure coverage

- **where:** `RunConfig, Main, AnalysisReport, RunCommand, and TextSummaryRenderer`
- **why:** a result is auditable only when its direct-edge mode and observed failure denominator travel with the verdict
- **alternative:** Infer replay semantics from a command line or show only would-miss cases — rejected: omitted mode and denominator make a zero-miss result ambiguous
- **design:** ../design.md#report-compatibility-and-evidence-wording
- **constitution:** §V
- **trust:** ✓ verified
