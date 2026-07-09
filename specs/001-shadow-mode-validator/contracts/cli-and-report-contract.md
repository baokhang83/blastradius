# Contract: CLI Invocation & Analysis Report

This tool has no network API. Its external interface is (a) the CLI it's invoked with and
(b) the JSON report it produces. Both are the contract other tooling (and the maintainer) can
depend on.

## CLI invocation

```
blastradius-validator run \
  --project-path <path>       # required: local git working copy of the target project
  --commits <N>                # required: size of the recent-commit window (FR-012)
  --report-out <path>          # required: file path to write the JSON AnalysisReport
  [--summary-out <path>]       # optional: file path for the rendered text summary; defaults to stdout
```

**Exit codes**:

| Code | Meaning |
|---|---|
| `0` | Run completed and produced a verdict of `PASS` |
| `1` | Run completed and produced a verdict of `FAIL` |
| `2` | Run could not complete at all (e.g., invalid `--project-path`, not a Maven/JUnit 5 project) — distinct from a `FAIL` verdict, which is a valid, successfully-produced result |

The PASS/FAIL distinction is encoded in the exit code specifically so the validator can be
scripted (e.g., "only proceed with further investment if exit code is 0") without parsing the
report, while a hard error (code 2) is never confusable with a legitimate FAIL verdict.

## AnalysisReport JSON schema

Top-level object, corresponding to the `AnalysisReport` entity in data-model.md:

```json
{
  "verdict": "PASS | FAIL",
  "runConfig": {
    "projectPath": "string",
    "commitWindowSize": "integer",
    "analyzedAt": "ISO-8601 timestamp"
  },
  "analyzedCommitPairs": [
    {
      "baseCommit": "sha",
      "headCommit": "sha",
      "changedFiles": [
        { "path": "string", "kind": "JAVA_SOURCE | NON_SOURCE", "changedClassName": "string | null" }
      ]
    }
  ],
  "excludedCommitPairs": [
    { "baseCommit": "sha", "headCommit": "sha", "reason": "string" }
  ],
  "wouldMissCases": [
    {
      "commitPair": { "baseCommit": "sha", "headCommit": "sha" },
      "test": { "className": "string", "methodName": "string | null" },
      "changedClasses": ["string"],
      "selectionReason": "string"
    }
  ],
  "flakyFailures": [
    {
      "commitPair": { "baseCommit": "sha", "headCommit": "sha" },
      "test": { "className": "string", "methodName": "string | null" }
    }
  ],
  "savingsSummary": {
    "totalTestExecutions": "integer",
    "totalSelected": "integer",
    "proportionSkipped": "number (0.0-1.0)",
    "fallbackDrivenSelections": "integer",
    "dependencyMatchedSelections": "integer",
    "newOrModifiedTestSelections": "integer"
  }
}
```

**Invariants** (validated by contract tests, not just documented):

- `verdict = "PASS"` if and only if `wouldMissCases` is empty. (FR-005)
- Every element of `excludedCommitPairs` is absent from `analyzedCommitPairs`, and vice versa
  (a commit pair is one or the other, never both, never neither if it was in range). (FR-009)
- `savingsSummary.fallbackDrivenSelections + savingsSummary.dependencyMatchedSelections +
  savingsSummary.newOrModifiedTestSelections = savingsSummary.totalSelected`. There are
  exactly three selection reasons that count as "selected" (FR-002, FR-006, FR-007); each
  is its own bucket so the three-way split accounts for 100% of `totalSelected`. (FR-008)
- Every `wouldMissCases[].commitPair` and `flakyFailures[].commitPair` refers to a pair present
  in `analyzedCommitPairs` (never an excluded pair — excluded pairs contribute no results).
  (FR-009)
- The report is fully self-contained: no field references external state (e.g., a database row
  or a re-run) required to interpret it. (FR-010, SC-005)

## Text summary (rendered, not authoritative)

A plain-text rendering of the same JSON, printed to stdout (or `--summary-out`), intended for a
human skimming a single run's outcome — e.g.:

```
Verdict: FAIL
Analyzed: 187 commit pairs (3 excluded — see report for reasons)
Would-miss cases: 1
  - commit a1b2c3d..e4f5g6h: com.example.OrderServiceTest#refundAppliesTax
    changed: com.example.tax.TaxCalculator
    not selected: no dependency match recorded

Savings: 4,213 / 5,940 test executions would have been skipped (70.9%)
  - dependency-matched: 3,890
  - fallback-driven: 323
Flaky failures observed: 2 (excluded from verdict)
```

This text is derived entirely from the JSON; it is not a second source of truth and any
disagreement between them is a bug in the renderer.
