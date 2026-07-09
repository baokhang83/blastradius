# Feature Specification: Shadow-Mode Test Selection Validator

**Feature Branch**: N/A — no git branch created for this spec (git extension not
installed in this SpecKit setup); tracked via spec directory
`specs/001-shadow-mode-validator`

**Created**: 2026-07-08

**Status**: Draft

**Input**: User description: "Shadow-Mode Dependency-Based Test Selection Validator —
a standalone harness that replays a real, large, open-source Java project's git
history, computes which tests dependency-based selection would have run at each
historical change, always executes the full suite to get ground truth, and produces
a go/no-go verdict (would-miss rate must be zero) plus an execution-savings estimate,
before any further investment in the Blastradius product."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Get a go/no-go verdict for a project's history (Priority: P1)

As the maintainer, I point the validator at a real Java project and a range of its
history, and I get back a single, unambiguous verdict: PASS if dependency-based
selection would never have missed a real test failure across that range, or FAIL
with the specific cases that would have been missed. This is the answer to "is this
idea even sound," and it must be trustworthy enough to decide whether to keep
building Blastradius at all.

**Why this priority**: This is the entire reason the validator exists. Without a
trustworthy verdict, no other output (savings estimates, reports) matters — an unsafe
selection mechanism is worthless regardless of how much time it appears to save.

**Independent Test**: Can be fully tested by running the validator against a small,
known range of commits (e.g., 5-10) on a real Maven/JUnit 5 project, and confirming
it executes the full suite at every commit in range, computes a selection decision
per test per commit, and emits one PASS or FAIL verdict for the run.

**Acceptance Scenarios**:

1. **Given** a target project and a commit range where no test's selected set ever
   excludes a test that actually failed, **When** the validator completes analysis,
   **Then** it reports a PASS verdict.
2. **Given** a target project and a commit range containing at least one commit where
   a test failed but was not in the selected set, **When** the validator completes
   analysis, **Then** it reports a FAIL verdict and does not report PASS.
3. **Given** any analyzed commit, **When** the validator computes selection for that
   commit, **Then** it has already obtained real pass/fail results for every test at
   that commit before comparing (ground truth is never itself skipped or inferred).

---

### User Story 2 - See execution-savings evidence (Priority: P2)

As the maintainer, once a run completes, I want to see how much test execution would
have been avoided if selection had been live, so that a PASS verdict is paired with
a concrete number that justifies (or fails to justify) building the real product.

**Why this priority**: A PASS verdict alone answers "is it safe" but not "is it
worth it." A selection mechanism that is safe but only ever skips 2% of tests may
not justify the product; this evidence is what turns a safety result into a business
decision.

**Independent Test**: Can be fully tested by taking a completed analysis run and
confirming the report states, in aggregate across the analyzed range, what
proportion or count of test executions were in the selected set versus the total
that ran.

**Acceptance Scenarios**:

1. **Given** a completed analysis run, **When** the summary report is produced,
   **Then** it states the total number of test executions that occurred, the total
   number that would have been selected, and the resulting proportion skipped.
2. **Given** a completed analysis run, **When** the summary report is produced,
   **Then** it distinguishes savings attributable to ordinary dependency-based
   selection from cases where a conservative fallback forced selection of
   everything (so the two are not conflated when judging real potential savings).

---

### User Story 3 - Investigate a FAIL verdict in detail (Priority: P3)

As the maintainer, when the verdict is FAIL, I want each would-miss case broken down
individually — which commit, which test, what changed, and why the test was not
selected — so I can tell whether it reveals a genuine soundness gap in the selection
approach or a fixable bug in this validator.

**Why this priority**: A bare FAIL verdict is not actionable. This is what makes the
validator a diagnostic tool rather than a pass/fail box, and is required before any
soundness issue it finds could be understood or addressed.

**Independent Test**: Can be fully tested by taking a run with a known FAIL verdict
and confirming that every would-miss case in the report includes the commit
identifier, the failing test's identity, the set of changes between the commit pair,
and the selection reasoning that led to the test being excluded.

**Acceptance Scenarios**:

1. **Given** a FAIL verdict, **When** the report is produced, **Then** every
   would-miss case is listed individually with commit, test identity, and the
   reason selection excluded that test.
2. **Given** a FAIL verdict with multiple would-miss cases, **When** the report is
   produced, **Then** no would-miss case is aggregated away or summarized out of the
   detailed listing.

---

### User Story 4 - Analysis survives broken commits (Priority: P4)

As the maintainer, when a historical commit in the analyzed range fails to build or
its suite cannot execute for reasons unrelated to the code change under test, I want
that commit pair logged and excluded from the results, so that one broken commit in
a long historical range does not waste the entire analysis run or silently corrupt
the verdict.

**Why this priority**: Real project histories contain broken commits. Without this,
a single bad commit partway through a long-running analysis could invalidate hours
of otherwise-valid work, which makes the validator impractical to actually use.

**Independent Test**: Can be fully tested by running the validator across a range
that includes at least one commit known not to build, and confirming the run
completes, that commit pair appears in an excluded/skipped list with a reason, and
the verdict and savings estimate are computed only from the remaining valid commit
pairs.

**Acceptance Scenarios**:

1. **Given** a commit pair where the project fails to build, **When** the validator
   reaches that pair, **Then** it logs the pair as excluded with a reason and
   continues to the next pair rather than terminating the run.
2. **Given** a run containing one or more excluded commit pairs, **When** the final
   report is produced, **Then** the excluded pairs are listed separately and are not
   counted toward the verdict or the savings estimate.

---

### Edge Cases

- A commit modifies only a test file, not production code: that test MUST always be
  selected for that commit (it is, by definition, changed).
- A commit introduces a brand-new test with no prior recorded baseline: it MUST
  always be selected (safe default; never silently skipped for lack of history).
- A commit's changes touch only non-Java-source artifacts (configuration, resource
  files, build files, database migrations): selection for that commit MUST fall back
  to "select everything," and this MUST be visibly labeled as fallback-driven rather
  than an ordinary dependency match.
- A commit pair produces no ground-truth failures at all (a "clean" commit): it MUST
  still be counted toward the aggregate savings estimate even though it cannot
  contribute a would-miss case.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The validator MUST accept a target Java/Maven project (already present
  on the local filesystem with full git history) and a specification of which range
  of historical commits on its default branch to analyze.
- **FR-002**: For each consecutive pair of commits in the analyzed range, the
  validator MUST determine changed production classes and compute, for every test in
  the suite, whether that test would be selected — either because its tracked
  dependencies intersect the changed classes, or because a conservative fallback
  rule applies to changes outside what dependency tracking can observe (FR-006).
- **FR-003**: For each analyzed commit pair, the validator MUST execute the complete,
  unmodified test suite at the later commit to obtain authoritative pass/fail
  results. The validator MUST NOT skip or omit execution of any test as a result of
  its own selection computation — selection is recorded for comparison only.
- **FR-004**: The validator MUST compare, per analyzed commit pair, the selected-test
  set against the ground-truth confirmed-failed-test set (per FR-013), and MUST
  record any confirmed-failed test that was excluded from the selected set as a
  would-miss case.
- **FR-005**: The validator MUST produce a single overall verdict for a completed
  run: PASS if zero would-miss cases occurred across every analyzed commit pair, or
  FAIL otherwise. A FAIL verdict MUST be accompanied by every would-miss case
  individually, each including the commit identifier, the affected test's identity,
  the classes that changed in that commit pair, and the reason the test was not
  selected.
- **FR-006**: The validator MUST treat changes to non-Java-source artifacts
  (resource files, build/dependency configuration, database migration scripts) as
  triggering selection of the entire suite for that commit pair, and MUST label such
  selections as fallback-driven, distinct from ordinary dependency-matched
  selections, in all reporting.
- **FR-007**: The validator MUST always select any test that is newly added or whose
  own test file was modified in a commit, regardless of whether a prior dependency
  baseline exists for it.
- **FR-008**: The validator MUST produce an aggregate savings summary for a
  completed run, stating the total test executions that occurred, the count that
  would have been selected, the resulting proportion that would have been skipped,
  and the proportion of selections attributable to fallback rules versus ordinary
  dependency matches.
- **FR-009**: When a commit pair fails to build, or its test suite cannot execute for
  reasons unrelated to the specific tests under analysis, the validator MUST record
  that commit pair as excluded with a stated reason, continue analyzing the
  remaining range, and exclude that pair from both the verdict computation and the
  savings summary.
- **FR-010**: The validator's verdict and reporting MUST be reproducible from its
  recorded output alone — a reviewer must be able to audit any would-miss case or
  savings figure without re-running the analysis.
- **FR-011**: The validator MUST support projects using JUnit 5 as their test
  framework, including multi-module Maven projects. Dependency tracking and test
  execution MUST correctly span module boundaries within a single Maven reactor
  build — a change to a production class in one module MUST be correctly attributed
  against tests in any other module of the same project that depends on it.
- **FR-012**: The validator MUST analyze a fixed, operator-configurable window of the
  most recent consecutive commits on the target project's default branch (rather
  than the full history or a sampled subset), so that run cost stays bounded and
  predictable. The window size is a parameter of the run (per FR-001), not a fixed
  constant of this specification.
- **FR-013**: When a test fails during ground-truth execution at a commit, the
  validator MUST re-run that specific test once more at the same commit to confirm
  the failure before counting it as a would-miss candidate. If the re-run passes,
  the original failure MUST be treated as flaky, excluded from would-miss
  consideration, and recorded separately per FR-014.
- **FR-014**: The validator MUST record tests whose failure did not reproduce on
  re-run (flaky failures) separately and visibly in the report, distinct from
  would-miss cases, so that flakiness inherent to the target project's own suite is
  transparent rather than silently discarded.

### Key Entities

- **Commit Pair**: a before/after pair of consecutive commits within the analyzed
  range; carries the set of changed files/classes between them (potentially spanning
  multiple modules of the same reactor build) and the ground-truth test results
  obtained at the later commit.
- **Selection Decision**: for one test within one commit pair, whether the test was
  selected, and the reason (a specific changed-class dependency match, potentially
  across a module boundary, or a named conservative fallback rule).
- **Would-Miss Case**: a specific instance where a test's failure was confirmed (via
  re-run per FR-013) at a commit but the test was not selected for that commit;
  carries the commit identifier, the test's identity, the relevant changes, and the
  selection reasoning that excluded it.
- **Flaky Failure**: a specific instance where a test failed once but passed on
  immediate re-run at the same commit; recorded separately from would-miss cases.
- **Analysis Report**: the run's complete output — overall verdict, the full
  would-miss list (if any), the flaky-failure list, the aggregate savings summary,
  and the list of excluded (unbuildable) commit pairs.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every completed validator run produces exactly one unambiguous
  verdict — PASS or FAIL — with no partial or unclear outcomes.
- **SC-002**: 100% of would-miss cases in a FAIL verdict are individually
  identifiable (specific commit and specific test) directly from the report, without
  needing to re-run the analysis or inspect raw logs.
- **SC-003**: A completed run yields a single, clear numeric answer to "what
  proportion of test executions would this have avoided," usable on its own as the
  evidence for a go/no-go product decision.
- **SC-004**: A run across a historical range containing at least one unbuildable
  commit still completes and produces a verdict, rather than terminating without a
  result.
- **SC-005**: A reviewer who did not run the analysis can audit any individual
  would-miss case or the savings figure using only the produced report.
- **SC-006**: Running the validator with the dependency-tracking agent attached adds
  no more than 20% wall-clock overhead to the target project's own native
  `mvn test`/`verify` time, measured across the analyzed commit range.

## Assumptions

- The target project builds with Maven and uses JUnit 5 as its test framework; this
  validation slice does not need to support other build tools. The target MAY be a
  multi-module Maven reactor build (per FR-011); single-module Maven projects are
  also supported as a special case of the same requirement.
- The target project is already checked out locally with complete git history
  available; acquiring or cloning the target project is a separate, external
  concern not covered by this feature.
- "Historical commits on the default branch" is the analyzed timeline for this
  slice; analyzing pull-request branches or non-default branches is out of scope.
- The analyzed commit window's size is chosen per run by the operator, based on the
  target project's suite runtime and desired confidence (per FR-012); this
  specification does not fix a specific number of commits.
- A test is confirmed-failed only if it fails on both its original run and the
  single confirmation re-run (per FR-013); if the re-run passes, the original
  failure is treated as flaky (per FR-014), not as a would-miss candidate.
- This validator is a standalone, offline tool run manually or on a schedule by the
  maintainer. It is not integrated into any live CI pipeline and does not affect any
  real test run outside of its own analysis — that integration is explicitly a later
  phase, contingent on this slice producing a PASS verdict.
- One validator run analyzes exactly one target project; comparing results across
  multiple projects in a single run is out of scope.
- No machine-learning component is involved in this slice's selection logic, per the
  constitution's Deterministic Core Before ML principle.
