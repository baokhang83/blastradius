# Phase 1 Data Model: Shadow-Mode Test Selection Validator

Derived from the spec's Key Entities. This is the shape of the data the validator produces and
reasons about — not a persistence schema (there is no database; see research.md #6).

## RunConfig

The operator-supplied input for a single validator run (FR-001, FR-012).

| Field | Type | Notes |
|---|---|---|
| `projectPath` | path | Local filesystem path to the target project's git working copy |
| `commitWindowSize` | int | Number of most-recent commits on the default branch to analyze (FR-012); operator-chosen, no fixed default in this spec |
| `reportOutputPath` | path | Where the JSON report (and derived summary) is written |

## CommitPair

One before/after pair of consecutive commits within the analyzed window.

| Field | Type | Notes |
|---|---|---|
| `baseCommit` | commit SHA | The earlier commit |
| `headCommit` | commit SHA | The later commit; ground truth is captured here |
| `changedFiles` | list\<ChangedFile\> | Files that differ between base and head |
| `status` | enum: `ANALYZED`, `EXCLUDED` | `EXCLUDED` when the pair could not be built/tested (FR-009) |
| `exclusionReason` | string, nullable | Present only when `status = EXCLUDED` |

## ChangedFile

| Field | Type | Notes |
|---|---|---|
| `path` | string | Repo-relative path |
| `kind` | enum: `JAVA_SOURCE`, `NON_SOURCE` | Drives whether ordinary dependency matching or a fallback rule (FR-006) applies |
| `changedClassName` | string, nullable | Fully-qualified class name, when `kind = JAVA_SOURCE` |

## TestIdentity

A stable identity for one test, used to correlate selection decisions with ground-truth
results across a commit pair.

| Field | Type | Notes |
|---|---|---|
| `className` | string | Fully-qualified test class name |
| `methodName` | string, nullable | Present for method-level identity where the ground-truth source (Surefire/Failsafe XML) provides it |

## SelectionDecision

For one test within one commit pair (Key Entity: Selection Decision).

| Field | Type | Notes |
|---|---|---|
| `test` | TestIdentity | |
| `selected` | boolean | |
| `reason` | enum: `DEPENDENCY_MATCH`, `FALLBACK_NON_SOURCE_CHANGE`, `NEW_OR_MODIFIED_TEST`, `NO_MATCH` | `NO_MATCH` means not selected; the others are why it *was* selected (FR-002, FR-006, FR-007) |
| `matchedChangedClass` | string, nullable | The specific changed class responsible, when `reason = DEPENDENCY_MATCH` |

## GroundTruthResult

The authoritative outcome for one test at one commit pair's head commit, after confirmation.

| Field | Type | Notes |
|---|---|---|
| `test` | TestIdentity | |
| `outcome` | enum: `PASSED`, `CONFIRMED_FAILED`, `FLAKY` | `FLAKY` when the original run failed but the single confirmation re-run passed (FR-013/014) |

## WouldMissCase

A confirmed failure that selection excluded (Key Entity: Would-Miss Case).

| Field | Type | Notes |
|---|---|---|
| `commitPair` | CommitPair reference | |
| `test` | TestIdentity | |
| `changedClasses` | list\<string\> | What changed in this commit pair |
| `selectionReason` | string | Why the test was *not* selected (drawn from the corresponding `SelectionDecision`) |

## FlakyFailure

Key Entity: Flaky Failure.

| Field | Type | Notes |
|---|---|---|
| `commitPair` | CommitPair reference | |
| `test` | TestIdentity | |

## AnalysisReport

The run's complete output (Key Entity: Analysis Report) — the single JSON source of truth
(research.md #6).

| Field | Type | Notes |
|---|---|---|
| `verdict` | enum: `PASS`, `FAIL` | PASS iff `wouldMissCases` is empty (FR-005) |
| `analyzedCommitPairs` | list\<CommitPair\> | Only pairs with `status = ANALYZED` |
| `excludedCommitPairs` | list\<CommitPair\> | Only pairs with `status = EXCLUDED` (FR-009) |
| `wouldMissCases` | list\<WouldMissCase\> | Empty iff verdict is PASS |
| `flakyFailures` | list\<FlakyFailure\> | Reported for transparency; never affects verdict |
| `savingsSummary` | SavingsSummary | |

## SavingsSummary

Supports FR-008 / SC-003.

| Field | Type | Notes |
|---|---|---|
| `totalTestExecutions` | int | Across all analyzed commit pairs |
| `totalSelected` | int | |
| `proportionSkipped` | double | `1 - (totalSelected / totalTestExecutions)` |
| `fallbackDrivenSelections` | int | Subset of `totalSelected` attributable to FR-006 fallback rules |
| `dependencyMatchedSelections` | int | Subset of `totalSelected` attributable to ordinary matching |

## Relationships

```
RunConfig ──produces──▶ AnalysisReport
AnalysisReport ──contains many──▶ CommitPair (ANALYZED | EXCLUDED)
CommitPair ──has many──▶ ChangedFile
CommitPair ──has many──▶ SelectionDecision (one per test)
CommitPair ──has many──▶ GroundTruthResult (one per test)
(SelectionDecision × GroundTruthResult, joined by TestIdentity)
  ──where selected=false AND outcome=CONFIRMED_FAILED──▶ WouldMissCase
  ──where outcome=FLAKY──▶ FlakyFailure
AnalysisReport ──has one──▶ SavingsSummary (aggregated from all SelectionDecisions)
```
