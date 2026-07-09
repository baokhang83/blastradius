# Blastradius

A Maven test-impact-selection validator for Java/JUnit 5 projects.

Blastradius investigates whether dynamic, class-level dependency tracking can safely
predict which tests a code change could break — so that CI eventually only needs to run
the tests actually affected by a change, not the entire suite. This repository is the
**shadow-mode validator**: it never skips real test execution. It always runs everything,
and only *afterward* checks whether its selection logic would have chosen the right
tests, comparing chosen tests against skipped ones.

## Why "shadow mode"?

Per this project's constitution, no selection mechanism is trusted to actually skip
tests until it has proven — on real commit history — that it never misses a real test
failure. This tool exists to produce that proof (or disproof) before any real
test-skipping plugin is ever built.

## How it works

Given a target Java/Maven project and a window of its N most recent commits, for each
consecutive commit pair the validator:

1. **Checks out the base commit non-destructively** (via a disposable scratch clone —
   the target repository's HEAD, branch, and working tree are never touched).
2. **Builds the base commit with a tracking agent attached** — a `-javaagent` using
   `java.lang.instrument` to observe every class loaded during each test, via bytecode
   checksums — recording each test's dependencies.
3. **Builds the head commit to get ground truth** — real pass/fail per test, with a
   single confirmation re-run for any failure to rule out flakiness before it's treated
   as a real regression.
4. **Classifies the changed files** between base and head (Java source vs. everything
   else — config, resources, `pom.xml`, migrations).
5. **Runs selection**: a test is selected if (a) one of its tracked dependencies
   changed, (b) it's new or was itself modified, or (c) a non-source-code change
   triggered a conservative "select everything" fallback.
6. **Compares selection against ground truth.** Any confirmed-failed test that wasn't
   selected is a "would-miss" case.
7. **Computes a verdict** — `PASS` only if there are zero would-miss cases across the
   whole analyzed window — plus a savings summary (how many test executions would have
   been skipped).

A commit pair whose base or head commit fails to build is excluded from the run (with a
recorded reason) rather than aborting the whole analysis. Tests that fail once but pass
on confirmation are reported separately as flaky — never affecting the verdict.

The result is written as a JSON report plus a rendered human-readable text summary.

Multi-module Maven reactors are supported: because tracking is based on actual class
loads rather than a static per-module dependency graph, cross-module dependencies are
attributed correctly without any extra bookkeeping.

## Usage

```bash
blastradius-validator run \
  --project-path /path/to/target/project \
  --commits 50 \
  --report-out report.json \
  [--summary-out summary.txt]
```

If `--summary-out` is omitted, the rendered text summary is printed to stdout.

Exit codes:
- `0` — verdict `PASS`
- `1` — verdict `FAIL` (see `report.json` for the specific would-miss cases)
- `2` — the run itself could not complete (bad arguments, build infrastructure failure, etc.)

## Report shape

```json
{
  "verdict": "PASS | FAIL",
  "analyzedCommitPairs": [ ... ],
  "excludedCommitPairs": [ { "baseCommit": "...", "headCommit": "...", "exclusionReason": "..." } ],
  "wouldMissCases": [ { "commitPair": {...}, "test": {...}, "changedClasses": [...], "selectionReason": "..." } ],
  "flakyFailures": [ { "commitPair": {...}, "test": {...} } ],
  "savingsSummary": {
    "totalTestExecutions": 0, "totalSelected": 0, "proportionSkipped": 0.0,
    "dependencyMatchedSelections": 0, "fallbackDrivenSelections": 0, "newOrModifiedTestSelections": 0
  }
}
```

## Project layout

```
src/main/java/io/github/baokhang83/blastradius/validator/
├── cli/         Main entry point, argument parsing, run orchestration
├── git/         Commit window resolution, non-destructive checkout, change classification
├── tracking/    The java.lang.instrument agent + per-test dependency attribution
├── build/       Driving the target project's own Maven build, ground-truth resolution,
│                build-failure detection
├── selection/   The selection engine and its three rules
├── verdict/     Would-miss comparison, flaky-failure records, and PASS/FAIL calculation
└── report/      JSON report model, writer, savings aggregation, text rendering
```

## Design principles (from the project constitution)

- **Test-Driven Development is non-negotiable.** Every piece of engine code was built
  red → green → refactor.
- **Safety over speed.** When in doubt, the tool selects more tests, not fewer.
- **Deterministic before ML.** Selection is pure, explainable dependency tracking —
  no machine learning, no probabilistic shortcuts.
- **Shadow-mode before gating.** This tool never skips a real test. It only reports
  what it *would* have skipped, and whether that would have been safe.
- **Explainability.** Every selection decision carries a concrete reason — dependency
  match, fallback, or new/modified test — never an opaque score.

## Known limitations

- A class loaded only inside a JUnit 5 `@BeforeAll` (a container-level callback, not a
  test) is never attributed to any specific test, since dependency tracking only
  attributes loads to tests that are actually executing. If such a class later changes
  and breaks a test that depended on it only via `@BeforeAll` setup, that dependency is
  invisible to selection — a narrow, deterministic, and honestly-documented edge case
  (not a bug we're hiding). If this matters for a target project, treat `@BeforeAll`-only
  dependencies with extra caution.
- Each commit pair currently builds the target project up to three times (base, head,
  and a possible single-test confirmation re-run) — correct, but not optimized for large
  analysis windows against slow-building projects.

## Status

The core pipeline — commit traversal, non-destructive checkout, dependency tracking,
ground truth resolution (with flaky confirmation), selection, would-miss comparison,
verdict computation, savings evidence, broken-commit resilience, and both JSON and
text reporting — is implemented and covered by an extensive test suite (90+ tests)
built through strict TDD. See `specs/001-shadow-mode-validator/` for the full spec,
plan, design decisions (ADR-style research notes), and task breakdown.
