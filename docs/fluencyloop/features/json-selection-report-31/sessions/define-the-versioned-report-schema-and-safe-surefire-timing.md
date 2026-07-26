# Session: define the versioned report schema and safe Surefire timing reader

- **intent:** define the versioned report schema and safe Surefire timing reader
- **started:** 2026-07-26

## Knowledge transfer

- **BuildReport schema:** The machine-readable artifact remains the source for console rendering and CI. It exposes schema version, changed files, every `SelectionReason` count, selected/skipped totals, and timing coverage; `estimatedTimeSavedMillis` is absent until the timing history covers every skipped test.
- **SurefireReportReader:** Standard Surefire/Failsafe XML provides the class name, method name, and seconds duration per testcase. The reader converts those to milliseconds and blocks external DTD/schema resolution because timing extraction never needs XML to load external resources.

## Decision: extend BuildReport as the single versioned report schema

- **where:** `blastradius-maven-plugin report package`
- **why:** The existing immutable report already feeds console output, so adding schema fields, changed-file context, reason buckets, and guarded timing estimates preserves one source of truth for CI and humans.
- **alternative:** Parallel JSON report model — rejected: it would duplicate selection state and allow the machine report to drift from console output.
- **design:** ../design.md#key-choice
- **constitution:** §II
- **trust:** ✓ verified

## Decision: disable external XML resolution while reading Surefire reports

- **where:** `blastradius-maven-plugin report/SurefireReportReader`
- **why:** Timing import needs only local testcase attributes, so secure processing and blocked external DTD/schema access prevent report data from causing local-file or network access.
- **alternative:** Default DOM parser configuration — rejected: it leaves external entity resolution enabled without serving the timing use case.
- **design:** ../design.md#class-diagram
- **constitution:** §VI
- **trust:** ✓ verified
